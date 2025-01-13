package mill.scalalib

import mill.api.Loose.Agg
import mill.api.{JarManifest, PathRef, Result}
import mill.define.{Target => T, _}

import scala.annotation.nowarn

/**
 * Core configuration required to compile a single Java compilation target
 */
trait AssemblyModule extends mill.Module {
  outer =>

  def finalMainClassOpt: T[Either[String, String]]

  def forkArgs: T[Seq[String]]

  def manifest: T[JarManifest]

  /**
   * What shell script to use to launch the executable generated by `assembly`.
   * Defaults to a generic "universal" launcher that should work for Windows,
   * OS-X and Linux
   */
  def prependShellScript: T[String] = Task {
    prependShellScript0()
  }
  private[mill] def prependShellScript0: T[String] = Task {
    finalMainClassOpt().toOption match {
      case None => ""
      case Some(cls) =>
        mill.util.Jvm.launcherUniversalScript(
          mainClass = cls,
          shellClassPath = Agg("$0"),
          cmdClassPath = Agg("%~dpnx0"),
          jvmArgs = forkArgs()
        )
    }
  }

  def assemblyRules: Seq[Assembly.Rule] = assemblyRules0

  private[mill] def assemblyRules0: Seq[Assembly.Rule] = Assembly.defaultRules

  def upstreamAssemblyClasspath: T[Agg[PathRef]]

  def localClasspath: T[Seq[PathRef]]

  private[mill] def upstreamAssembly2_0: T[Assembly] = Task {
    Assembly.create(
      destJar = T.dest / "out.jar",
      inputPaths = upstreamAssemblyClasspath().map(_.path),
      manifest = manifest(),
      assemblyRules = assemblyRules
    )
  }

  /**
   * Build the assembly for upstream dependencies separate from the current
   * classpath
   *
   * This should allow much faster assembly creation in the common case where
   * upstream dependencies do not change
   */
  def upstreamAssembly2: T[Assembly] = Task {
    upstreamAssembly2_0()
  }

  def upstreamAssembly: T[PathRef] = Task {
    T.log.error(
      s"upstreamAssembly target is deprecated and should no longer used." +
        s" Please make sure to use upstreamAssembly2 instead."
    )
    upstreamAssembly2().pathRef
  }

  private[mill] def assembly0: Task[PathRef] = Task.Anon {
    // detect potential inconsistencies due to `upstreamAssembly` deprecation after 0.11.7
    if (
      (upstreamAssembly.ctx.enclosing: @nowarn) != s"${classOf[AssemblyModule].getName}#upstreamAssembly"
    ) {
      T.log.error(
        s"${upstreamAssembly.ctx.enclosing: @nowarn} is overriding a deprecated target which is no longer used." +
          s" Please make sure to override upstreamAssembly2 instead."
      )
    }

    val prependScript = Option(prependShellScript()).filter(_ != "")
    val upstream = upstreamAssembly2()

    val created = Assembly.create(
      destJar = T.dest / "out.jar",
      Agg.from(localClasspath().map(_.path)),
      manifest(),
      prependScript,
      Some(upstream.pathRef.path),
      assemblyRules
    )
    // See https://github.com/com-lihaoyi/mill/pull/2655#issuecomment-1672468284
    val problematicEntryCount = 65535
    if (
      prependScript.isDefined &&
      (upstream.addedEntries + created.addedEntries) > problematicEntryCount
    ) {
      Result.Failure(
        s"""The created assembly jar contains more than ${problematicEntryCount} ZIP entries.
           |JARs of that size are known to not work correctly with a prepended shell script.
           |Either reduce the entries count of the assembly or disable the prepended shell script with:
           |
           |  def prependShellScript = ""
           |""".stripMargin,
        Some(created.pathRef)
      )
    } else {
      Result.Success(created.pathRef)
    }
  }

  /**
   * An executable uber-jar/assembly containing all the resources and compiled
   * classfiles from this module and all it's upstream modules and dependencies
   */
  def assembly: T[PathRef] = T {
    assembly0()
  }
}
