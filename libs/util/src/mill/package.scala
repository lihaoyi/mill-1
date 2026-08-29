/**
 * API documentation for the Mill JVM build tool. This package contains all the Mill APIs
 * exposed for you to use in your `build.mill` and `package.mill` files. Language-agnostic
 * APIs are mostly in [[mill.api]] and [[mill.util]], while `*lib` packages like [[mill.javalib]],
 * [[mill.scalalib]], and [[mill.kotlinlib]] contain the language-specific toolchains.
 */
package object mill {

  /**
   * Preserve `os.Path`'s absolute-path semantics when it becomes a process argument. `os.proc`
   * renders arguments before `.call` or `.spawn` receives the child cwd, so a serialized path
   * alias cannot be rebased at process-launch time.
   */
  implicit def pathToShellable(path: os.Path): os.Shellable =
    os.Shellable(Seq(mill.api.PathRef.toAbsString(path)))
}
