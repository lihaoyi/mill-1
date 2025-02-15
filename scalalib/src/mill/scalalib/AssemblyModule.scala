package mill.scalalib

import mill.api.{PathRef, Result}
import mill.util.JarManifest
import mill.define.{Target => T, _}
import mill.util.Jvm

/**
 * Core configuration required to compile a single Java compilation target
 */
trait AssemblyModule extends mill.Module {
  outer =>

  def finalMainClassOpt: T[Either[String, String]]

  def forkArgs: T[Seq[String]]

  /**
   * Similar to `forkArgs` but only applies to the `sh` launcher script
   */
  def forkShellArgs: T[Seq[String]] = Task { Seq.empty[String] }

  /**
   * Similar to `forkArgs` but only applies to the `bat` launcher script
   */
  def forkCmdArgs: T[Seq[String]] = Task { Seq.empty[String] }

  /**
   * Creates a manifest representation which can be modified or replaced
   * The default implementation just adds the `Manifest-Version`, `Main-Class` and `Created-By` attributes
   */
  def manifest: T[JarManifest] = Task { manifest0() }

  private[mill] def manifest0: T[JarManifest] = Task {
    Jvm.createManifest(finalMainClassOpt().toOption)
  }

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
          shellClassPath = Seq("$0"),
          cmdClassPath = Seq("%~dpnx0"),
          jvmArgs = forkArgs(),
          shebang = false,
          shellJvmArgs = forkShellArgs(),
          cmdJvmArgs = forkCmdArgs()
        )
    }
  }

  def assemblyRules: Seq[Assembly.Rule] = assemblyRules0

  private[mill] def assemblyRules0: Seq[Assembly.Rule] = Assembly.defaultRules

  def resolvedIvyAssemblyClasspath: T[Seq[PathRef]]

  def upstreamAssemblyClasspath: T[Seq[PathRef]]

  def localClasspath: T[Seq[PathRef]]

  /**
   * Build the assembly for third-party dependencies separate from the current
   * classpath
   *
   * This should allow much faster assembly creation in the common case where
   * third-party dependencies do not change
   */
  def resolvedIvyAssembly: T[Assembly] = Task {
    Assembly.create(
      destJar = Task.dest / "out.jar",
      inputPaths = resolvedIvyAssemblyClasspath().map(_.path),
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
  def upstreamAssembly: T[Assembly] = Task {
    Assembly.create(
      destJar = Task.dest / "out.jar",
      inputPaths = upstreamAssemblyClasspath().map(_.path),
      manifest = manifest(),
      base = Some(resolvedIvyAssembly().pathRef.path),
      assemblyRules = assemblyRules
    )
  }

  /**
   * An executable uber-jar/assembly containing all the resources and compiled
   * classfiles from this module and all it's upstream modules and dependencies
   */
  def assembly: T[PathRef] = Task {
    val prependScript = Option(prependShellScript()).filter(_ != "")
    val upstream = upstreamAssembly()

    val created = Assembly.create(
      destJar = Task.dest / "out.jar",
      inputPaths = Seq.from(localClasspath().map(_.path)),
      manifest = manifest(),
      prependShellScript = prependScript,
      base = Some(upstream.pathRef.path),
      assemblyRules = assemblyRules
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
           |""".stripMargin
      )
    } else {
      Result.Success(created.pathRef)
    }
  }
}
