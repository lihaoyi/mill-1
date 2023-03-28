package mill.runner

import mill._
import mill.api.{PathRef, Result, internal}
import mill.define.{Caller, Discover, Task}
import mill.scalalib.{BoundDep, DepSyntax, Lib, ScalaModule, Versions}
import pprint.Util.literalize

/**
 * Mill module for pre-processing a Mill `build.sc` and related files and then
 * compiling them as a normal [[ScalaModule]]. Parses `build.sc`, walks any
 * `import $file`s, wraps the script files to turn them into valid Scala code
 * and then compiles them with the `ivyDeps` extracted from the `import $ivy`
 * calls within the scripts.
 *
 * Also dumps the [[scriptImportGraph]] for the downstream Evaluator to use for
 * fine-grained task invalidation based the import relationship between the file
 * defining the task and any files which were changed.
 */
@internal
class MillBuildModule()(implicit baseModuleInfo: BaseModule.Info,
                        millBuildModuleInfo: MillBuildModule.Info) extends BaseModule() with ScalaModule{

  def millSourcePath = millBuildModuleInfo.projectRoot / os.up / "mill-build"

  def resolveDeps(deps: Task[Agg[BoundDep]], sources: Boolean = false): Task[Agg[PathRef]] = T.task {
    // We need to resolve the sources to make GenIdeaExtendedTests pass for
    // some reason, but we don't need to actually return them (???)
    val unused = super.resolveDeps(deps, true)()

    super.resolveDeps(deps, false)()
  }

  def scalaVersion = "2.13.10"

  def parseBuildFiles = T.input {
    FileImportGraph.parseBuildFiles(
      millBuildModuleInfo.topLevelProjectRoot,
      millBuildModuleInfo.projectRoot / os.up
    )
  }

  def ivyDeps = T {
    Agg.from(
      parseBuildFiles().ivyDeps
        .map(str =>
          mill.scalalib.Dep.parse(
            str
              .replace("$MILL_VERSION", mill.BuildInfo.millVersion)
              .replace("${MILL_VERSION}", mill.BuildInfo.millVersion)
              .replace("$MILL_BIN_PLATFORM", mill.BuildInfo.millBinPlatform)
              .replace("${MILL_BIN_PLATFORM}", mill.BuildInfo.millBinPlatform)
          )
        )
    ) ++
      Seq(ivy"com.lihaoyi::mill-moduledefs:${Versions.millModuledefsVersion}")
  }

  def scriptSources = T.sources {
    for ((p, s) <- parseBuildFiles().seenScripts.toSeq) yield PathRef(p)
  }

  def generatedSources = T {
    generateScriptSources()
  }

  def generateScriptSources = T{
    val parsed = parseBuildFiles()
    if (parsed.errors.nonEmpty) Result.Failure(parsed.errors.mkString("\n"))
    else {
      MillBuildModule.generateWrappedSources(
        millBuildModuleInfo.projectRoot / os.up,
        scriptSources(),
        parsed.seenScripts,
        T.dest,
        millBuildModuleInfo.enclosingClasspath,
        millBuildModuleInfo.topLevelProjectRoot
      )
      Result.Success(Seq(PathRef(T.dest)))
    }
  }

  def scriptImportGraph = T {
    parseBuildFiles()
      .importGraphEdges
      .map{case (path, imports) =>
        (path, (PathRef(path).hashCode(), imports))
      }
  }

  override def allSourceFiles: T[Seq[PathRef]] = T {
    Lib.findSourceFiles(allSources(), Seq("scala", "java", "sc")).map(PathRef(_))
  }

  def unmanagedClasspath = mill.define.Target.input {
    mill.api.Loose.Agg.from(
      millBuildModuleInfo.enclosingClasspath.map(p => mill.api.PathRef(p, quick = true))
    ) ++
    lineNumberPluginClasspath()
  }

  def scalacPluginIvyDeps = Agg(
    ivy"com.lihaoyi:::scalac-mill-moduledefs-plugin:${Versions.millModuledefsVersion}"
  )

  def scalacOptions = T {
    super.scalacOptions() ++
      Seq("-Xplugin:" + lineNumberPluginClasspath().map(_.path).mkString(","), "-nowarn")
  }

  def scalacPluginClasspath = super.scalacPluginClasspath() ++ lineNumberPluginClasspath()

  def lineNumberPluginClasspath: T[Agg[PathRef]] = T {
    mill.modules.Util.millProjectModule(
      "MILL_LINENUMBERS",
      "mill-runner-linenumbers",
      repositoriesTask()
    )
  }
}

object MillBuildModule{

  class BootstrapModule(topLevelProjectRoot0: os.Path,
                        projectRoot: os.Path,
                        enclosingClasspath: Seq[os.Path])
                       (implicit baseModuleInfo: BaseModule.Info) extends BaseModule {

    implicit private def millBuildModuleInfo = MillBuildModule.Info(
      enclosingClasspath, projectRoot, topLevelProjectRoot0
    )
    object build extends MillBuildModule

    override lazy val millDiscover: Discover[this.type] =
      baseModuleInfo.discover.asInstanceOf[Discover[this.type]]
  }

  case class Info(enclosingClasspath: Seq[os.Path],
                  projectRoot: os.Path,
                  topLevelProjectRoot: os.Path)

  def generateWrappedSources(base: os.Path,
                             scriptSources: Seq[PathRef],
                             scriptCode: Map[os.Path, String],
                             targetDest: os.Path,
                             enclosingClasspath: Seq[os.Path],
                             millTopLevelProjectRoot: os.Path) = {
    for (scriptSource <- scriptSources) {
      val relative = scriptSource.path.relativeTo(base)
      val dest = targetDest / FileImportGraph.fileImportToSegments(base, scriptSource.path, false)

      val newSource = MillBuildModule.top(
        relative,
        scriptSource.path / os.up,
        FileImportGraph.fileImportToSegments(base, scriptSource.path, true).dropRight(1),
        scriptSource.path.baseName,
        enclosingClasspath,
        millTopLevelProjectRoot,
        scriptSource.path
      ) +
        scriptCode(scriptSource.path) +
        MillBuildModule.bottom


      os.proc("cat", os.temp(newSource)).call(stdout = os.Inherit)

      os.write(dest, newSource , createFolders = true)
    }
  }

  def top(relative: os.RelPath,
          base: os.Path,
          pkg: Seq[String],
          name: String,
          enclosingClasspath: Seq[os.Path],
          millTopLevelProjectRoot: os.Path,
          originalFilePath: os.Path) = {

    val superClass =
      if (pkg.size <= 1 && name == "build") "_root_.mill.runner.BaseModule"
      else {
        // Computing a path in "out" that uniquely reflects the location
        // of the foreign module relatively to the current build.

        // Encoding the number of `/..`
        val ups = if (relative.ups > 0) Seq(s"up-${relative.ups}") else Seq()
        val segs =
          Seq("foreign-modules") ++
          ups ++
          relative.segments.init ++
          Seq(relative.segments.last.stripSuffix(".sc"))

        val segsList = segs.map(pprint.Util.literalize(_)).mkString(", ")
        s"_root_.mill.runner.BaseModule.Foreign(Some(_root_.mill.define.Segments.labels($segsList)))"
      }

    s"""
       |package ${pkg.mkString(".")}
       |import _root_.mill._
       |object `MiscInfo_${name}`{
       |  implicit val millBuildModuleInfo: _root_.mill.runner.MillBuildModule.Info = _root_.mill.runner.MillBuildModule.Info(
       |    ${enclosingClasspath.map(p => literalize(p.toString))}.map(_root_.os.Path(_)),
       |    _root_.os.Path(${literalize(base.toString)}),
       |    _root_.os.Path(${literalize(millTopLevelProjectRoot.toString)})
       |  )
       |  import mill.main.TokenReaders._
       |  implicit val millBaseModuleInfo: _root_.mill.runner.BaseModule.Info = _root_.mill.runner.BaseModule.Info(
       |    millBuildModuleInfo.projectRoot,
       |    _root_.mill.define.Discover[$name]
       |  )
       |}
       |import `MiscInfo_${name}`.{millBuildModuleInfo, millBaseModuleInfo}
       |object $name extends $name
       |class $name extends $superClass{
       |
       |//MILL_ORIGINAL_FILE_PATH=${originalFilePath}
       |//MILL_USER_CODE_START_MARKER
       |""".stripMargin
  }

  val bottom = "\n}"


}
