package mill.main.maven

import mainargs.{Flag, ParserForClass, arg, main}
import mill.main.buildgen.BuildGenUtil.*
import mill.main.buildgen.{
  BuildGenUtil,
  BuildObject,
  IrBuild,
  IrPom,
  IrScopedDeps,
  IrTrait,
  Node,
  Tree
}
import org.apache.maven.model.{Dependency, Model, Parent}

import scala.collection.immutable.SortedMap
import scala.jdk.CollectionConverters.*

/**
 * Converts a Maven build to Mill by generating Mill build file(s) from POM file(s).
 *
 * The generated output should be considered scaffolding and will likely require edits to complete conversion.
 *
 * ===Capabilities===
 * The conversion
 *  - handles deeply nested modules
 *  - captures project settings
 *  - configures dependencies for scopes:
 *    - compile
 *    - provided
 *    - runtime
 *    - test
 *  - configures testing frameworks:
 *    - JUnit 4
 *    - JUnit 5
 *    - TestNG
 *  - configures multiple, compile and test, resource directories
 *
 * ===Limitations===
 * The conversion does not support:
 *  - plugins, other than maven-compiler-plugin
 *  - packaging, other than jar, pom
 *  - build extensions
 *  - build profiles
 */
@mill.api.internal
object BuildGen {

  def main(args: Array[String]): Unit = {
    val cfg = ParserForClass[BuildGenConfig].constructOrExit(args.toSeq)
    run(cfg)
  }

  private type MavenNode = Node[Model]

  private def run(cfg: BuildGenConfig): Unit = {
    val workspace = os.pwd

    println("converting Maven build")
    val modeler = Modeler(cfg)
    val input = Tree.from(Seq.empty[String]) { dirs =>
      val model = modeler(workspace / dirs)
      (Node(dirs, model), model.getModules.iterator().asScala.map(dirs :+ _))
    }

    val output = convert(input, cfg)
    writeBuildObject(if (cfg.merge.value) compactBuildTree(output) else output)

    println("converted Maven build to Mill")
  }

  private def convert(input: Tree[MavenNode], cfg: BuildGenConfig): Tree[Node[BuildObject]] = {
    val packages = // for resolving moduleDeps
      buildPackages(input)(model => (model.getGroupId, model.getArtifactId, model.getVersion))

    val moduleSupertypes = Seq("PublishModule", "MavenModule")

    val (
      baseJavacOptions,
      baseRepos,
      baseNoPom,
      basePublishVersion,
      basePublishProperties,
      baseModuleTypedef
    ) = cfg.shared.baseModule match {
      case Some(baseModule) =>
        val model = input.node.value
        val javacOptions = Plugins.MavenCompilerPlugin.javacOptions(model)
        val pomSettings = extractPomSettings(model)
        val publishVersion = model.getVersion
        val publishProperties = getPublishProperties(model, cfg)

        val typedef = IrTrait(
          cfg.shared.jvmId,
          baseModule,
          moduleSupertypes,
          javacOptions,
          pomSettings,
          publishVersion,
          publishProperties
        )

        (javacOptions, Seq.empty, false, publishVersion, publishProperties, typedef)
      case None =>
        (Seq.empty, Seq.empty, true, "", Seq.empty, null)
    }

    input.map { case build @ Node(dirs, model) =>
      val artifactId = model.getArtifactId
      println(s"converting module $artifactId")

      val millSourcePath = os.Path(model.getProjectDirectory)
      val packaging = model.getPackaging

      val isNested = dirs.nonEmpty
      val hasTest = os.exists(millSourcePath / "src/test")

      val supertypes =
        Seq("RootModule") ++
          (cfg.shared.baseModule match {
            case None => moduleSupertypes
            case Some(v) => Seq(v)
          })

      val scopedDeps = extractScopedDeps(model, packages, cfg)

      val inner = {
        val options = Plugins.MavenCompilerPlugin.javacOptions(model)
        val javacOptions = if (options == baseJavacOptions) Seq.empty else options

        val pomSettings = if (baseNoPom) extractPomSettings(model) else null
        val resources = model.getBuild.getResources.iterator().asScala
          .map(_.getDirectory)
          .map(os.Path(_).subRelativeTo(millSourcePath))
          .filterNot(_ == mavenMainResourceDir)
        val version = model.getVersion
        val publishVersion = if (version == basePublishVersion) null else version

        val publishProperties = getPublishProperties(model, cfg).diff(basePublishProperties)
        val pomParentArtifact = mkPomParent(model.getParent)

        val testResources = model.getBuild.getTestResources.iterator().asScala
          .map(_.getDirectory)
          .map(os.Path(_).subRelativeTo(millSourcePath))
          .filterNot(_ == mavenTestResourceDir)

        IrBuild(
          scopedDeps,
          cfg.shared.testModule,
          hasTest,
          dirs,
          repos = Nil,
          javacOptions,
          artifactId,
          pomSettings,
          publishVersion,
          packaging,
          pomParentArtifact
        )
      }

      build.copy(value =
        BuildObject(
          renderImports(cfg.shared.baseModule, isNested, packages.size),
          scopedDeps.companions,
          supertypes,
          BuildGenUtil.renderIrBuild(inner),
          if (isNested || baseModuleTypedef == null) ""
          else BuildGenUtil.renderIrTrait(baseModuleTypedef)
        )
      )
    }
  }

  def gav(dep: Dependency): (String, String, String) =
    (dep.getGroupId, dep.getArtifactId, dep.getVersion)

  def getPublishProperties(model: Model, cfg: BuildGenConfig): Seq[(String, String)] =
    if (cfg.publishProperties.value) {
      val props = model.getProperties
      props.stringPropertyNames().iterator().asScala
        .map(key => (key, props.getProperty(key)))
        .toSeq
        .sorted
    } else Seq.empty

  def interpIvy(dep: Dependency): String =
    BuildGenUtil.renderIvyString(
      dep.getGroupId,
      dep.getArtifactId,
      dep.getVersion,
      dep.getType,
      dep.getClassifier,
      dep.getExclusions.iterator().asScala.map(x => (x.getGroupId, x.getArtifactId))
    )

  def mkPomParent(parent: Parent): String =
    if (null == parent) null
    else renderArtifact(parent.getGroupId, parent.getArtifactId, parent.getVersion)

  def extractPomSettings(model: Model): IrPom = {
    val licenses = model.getLicenses.iterator().asScala
      .map(lic =>
        mrenderLicense(
          lic.getName,
          lic.getName,
          lic.getUrl,
          isOsiApproved = false,
          isFsfLibre = false,
          "repo"
        )
      )
    val versionControl = Option(model.getScm).fold(renderVersionControl())(scm =>
      renderVersionControl(scm.getUrl, scm.getConnection, scm.getDeveloperConnection, scm.getTag)
    )
    val developers = model.getDevelopers.iterator().asScala
      .map(dev =>
        renderDeveloper(
          dev.getId,
          dev.getName,
          dev.getUrl,
          dev.getOrganization,
          dev.getOrganizationUrl
        )
      )

    IrPom(
      model.getDescription,
      model.getGroupId, // Mill uses group for POM org
      model.getUrl,
      licenses,
      versionControl,
      developers
    )
  }

  def extractScopedDeps(
      model: Model,
      packages: PartialFunction[(String, String, String), String],
      cfg: BuildGenConfig
  ): IrScopedDeps = {
    var sd = IrScopedDeps()

    val hasTest = os.exists(os.Path(model.getProjectDirectory) / "src/test")
    val ivyDep: Dependency => String = {
      cfg.shared.depsObject.fold(interpIvy(_)) { objName => dep =>
        {
          val depName = s"`${dep.getGroupId}:${dep.getArtifactId}`"
          sd = sd.copy(namedIvyDeps = sd.namedIvyDeps :+ ((depName, interpIvy(dep))))
          s"$objName.$depName"
        }
      }
    }

    model.getDependencies.forEach { dep =>
      val id = gav(dep)
      dep.getScope match {

        case "compile" =>
          if (packages.isDefinedAt(id))
            sd = sd.copy(mainCompileModuleDeps = sd.mainCompileModuleDeps + packages(id))
          else {
            if (isBom(id)) println(s"assuming compile dependency $id is a BOM")
            val ivy = ivyDep(dep)
            sd = sd.copy(mainIvyDeps = sd.mainIvyDeps + ivy)
          }
        case "provided" =>
          if (packages.isDefinedAt(id))
            sd = sd.copy(mainModuleDeps = sd.mainModuleDeps + packages(id))
          else {
            val ivy = ivyDep(dep)
            sd = sd.copy(mainCompileIvyDeps = sd.mainCompileIvyDeps + ivy)
          }
        case "runtime" =>
          if (packages.isDefinedAt(id))
            sd = sd.copy(mainRunModuleDeps = sd.mainRunModuleDeps + packages(id))
          else {
            val ivy = ivyDep(dep)
            sd = sd.copy(mainRunIvyDeps = sd.mainRunIvyDeps + ivy)
          }

        case "test" =>
          if (packages.isDefinedAt(id))
            sd = sd.copy(testModuleDeps = sd.testModuleDeps + packages(id))
          else {
            val ivy = ivyDep(dep)
            if (isBom(id)) {
              sd = sd.copy(testBomIvyDeps = sd.testBomIvyDeps + ivy)
            } else {
              sd = sd.copy(testIvyDeps = sd.testIvyDeps + ivy)
            }
          }

        case scope =>
          println(s"ignoring $scope dependency $id")

      }
      if (hasTest && sd.testModule.isEmpty) {
        sd = sd.copy(testModule = testModulesByGroup.get(dep.getGroupId))
      }
    }

    sd.copy(companions =
      cfg.shared.depsObject.fold(SortedMap.empty[String, BuildObject.Constants])(name =>
        SortedMap((name, SortedMap(sd.namedIvyDeps.toSeq *)))
      )
    )
  }
}

@main
@mill.api.internal
case class BuildGenConfig(
    shared: BuildGenUtil.Config,
    @arg(doc = "merge build files generated for a multi-module build", short = 'm')
    merge: Flag = Flag(),
    @arg(doc = "capture properties defined in `pom.xml` for publishing", short = 'p')
    publishProperties: Flag = Flag(),
    @arg(doc = "use cache for Maven repository system")
    cacheRepository: Flag = Flag(),
    @arg(doc = "process Maven plugin executions and configurations")
    processPlugins: Flag = Flag()
) extends ModelerConfig
