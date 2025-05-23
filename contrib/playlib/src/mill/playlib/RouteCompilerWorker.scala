package mill.playlib

import mill.define.{TaskCtx, PathRef}
import mill.api.Result
import mill.playlib.api.{RouteCompilerType, RouteCompilerWorkerApi}
import mill.scalalib.api.CompilationResult
import mill.Task

private[playlib] class RouteCompilerWorker extends AutoCloseable {

  private var routeCompilerInstanceCache =
    Option.empty[(Long, mill.playlib.api.RouteCompilerWorkerApi)]

  protected def bridge(toolsClasspath: Seq[PathRef])(
      implicit ctx: TaskCtx
  ): RouteCompilerWorkerApi = {
    val classloaderSig = toolsClasspath.hashCode
    routeCompilerInstanceCache match {
      case Some((sig, bridge)) if sig == classloaderSig => bridge
      case _ =>
        val toolsClassPath = toolsClasspath.map(_.path).toVector
        ctx.log.debug("Loading classes from\n" + toolsClassPath.mkString("\n"))
        val cl = mill.util.Jvm.createClassLoader(
          toolsClassPath,
          null,
          sharedLoader = getClass().getClassLoader(),
          sharedPrefixes = Seq("mill.playlib.api.")
        )
        val bridge = cl
          .loadClass("mill.playlib.worker.RouteCompilerWorker")
          .getDeclaredConstructor()
          .newInstance()
          .asInstanceOf[mill.playlib.api.RouteCompilerWorkerApi]
        routeCompilerInstanceCache = Some((classloaderSig, bridge))
        bridge
    }
  }

  def compile(
      routerClasspath: Seq[PathRef],
      files: Seq[os.Path],
      additionalImports: Seq[String],
      forwardsRouter: Boolean,
      reverseRouter: Boolean,
      namespaceReverseRouter: Boolean,
      generatorType: RouteCompilerType,
      dest: os.Path
  )(implicit ctx: TaskCtx): Result[CompilationResult] = {
    // the routes file must come last as it can include the routers generated
    // by the others
    bridge(routerClasspath)
      .compile(
        files.toArray.map(_.toIO),
        additionalImports.toArray,
        forwardsRouter,
        reverseRouter,
        namespaceReverseRouter,
        generatorType,
        dest.toIO
      ) match {
      case null =>
        Result.Success(CompilationResult(Task.dest / "zinc", PathRef(Task.dest)))
      case err => Result.Failure(err)
    }
  }

  override def close(): Unit = {
    routeCompilerInstanceCache = None
  }
}
