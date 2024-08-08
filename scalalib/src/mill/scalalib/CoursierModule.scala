package mill.scalalib

import coursier.cache.FileCache
import coursier.{Dependency, Repository, Resolve}
import coursier.core.Resolution
import mill.define.Task
import mill.api.PathRef

import scala.annotation.nowarn
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import mill.Agg

/**
 * This module provides the capability to resolve (transitive) dependencies from (remote) repositories.
 *
 * It's mainly used in [[JavaModule]], but can also be used stand-alone,
 * in which case you must provide repositories by overriding [[CoursierModule.repositoriesTask]].
 */
trait CoursierModule extends mill.Module {

  /**
   * Bind a dependency ([[Dep]]) to the actual module contetxt (e.g. the scala version and the platform suffix)
   * @return The [[BoundDep]]
   */
  def bindDependency: Task[Dep => BoundDep] = Task.anon { dep: Dep =>
    BoundDep((resolveCoursierDependency(): @nowarn).apply(dep), dep.force)
  }

  @deprecated("To be replaced by bindDependency", "Mill after 0.11.0-M0")
  def resolveCoursierDependency: Task[Dep => coursier.Dependency] = Task.anon {
    Lib.depToDependencyJava(_: Dep)
  }

  def defaultResolver: Task[CoursierModule.Resolver] = Task.anon {
    new CoursierModule.Resolver(
      repositories = repositoriesTask(),
      bind = bindDependency(),
      mapDependencies = Some(mapDependencies()),
      customizer = resolutionCustomizer(),
      coursierCacheCustomizer = coursierCacheCustomizer(),
      ctx = Some(implicitly[mill.api.Ctx.Log])
    )
  }

  /**
   * Task that resolves the given dependencies using the repositories defined with [[repositoriesTask]].
   *
   * @param deps    The dependencies to resolve.
   * @param sources If `true`, resolve source dependencies instead of binary dependencies (JARs).
   * @return The [[PathRef]]s to the resolved files.
   */
  def resolveDeps(deps: Task[Agg[BoundDep]], sources: Boolean = false): Task[Agg[PathRef]] =
    Task.anon {
      Lib.resolveDependencies(
        repositories = repositoriesTask(),
        deps = deps(),
        sources = sources,
        mapDependencies = Some(mapDependencies()),
        customizer = resolutionCustomizer(),
        coursierCacheCustomizer = coursierCacheCustomizer(),
        ctx = Some(implicitly[mill.api.Ctx.Log])
      )
    }

  /**
   * Map dependencies before resolving them.
   * Override this to customize the set of dependencies.
   */
  def mapDependencies: Task[Dependency => Dependency] = Task.anon { d: Dependency => d }

  /**
   * The repositories used to resolved dependencies with [[resolveDeps()]].
   */
  def repositoriesTask: Task[Seq[Repository]] = Task.anon {
    import scala.concurrent.ExecutionContext.Implicits.global
    val repos = Await.result(
      Resolve().finalRepositories.future(),
      Duration.Inf
    )
    repos
  }

  /**
   * Customize the coursier resolution resolution process.
   * This is rarely needed to changed, as the default try to provide a
   * highly reproducible resolution process. But sometime, you need
   * more control, e.g. you want to add some OS or JDK specific resolution properties
   * which are sometimes used by Maven and therefore found in dependency artifact metadata.
   * For example, the JavaFX artifacts are known to use OS specific properties.
   * To fix resolution for JavaFX, you could override this task like the following:
   * {{{
   *     override def resolutionCustomizer = Task.anon {
   *       Some( (r: coursier.core.Resolution) =>
   *         r.withOsInfo(coursier.core.Activation.Os.fromProperties(sys.props.toMap))
   *       )
   *     }
   * }}}
   * @return
   */
  def resolutionCustomizer: Task[Option[Resolution => Resolution]] = Task.anon { None }

  /**
   * Customize the coursier file cache.
   *
   * This is rarely needed to be changed, but sometimes e.g you want to load a coursier plugin.
   * Doing so requires adding to coursier's classpath. To do this you could use the following:
   * {{{
   *   override def coursierCacheCustomizer = Task.anon {
   *      Some( (fc: coursier.cache.FileCache[Task]) =>
   *        fc.withClassLoaders(Seq(classOf[coursier.cache.protocol.S3Handler].getClassLoader))
   *      )
   *   }
   * }}}
   * @return
   */
  def coursierCacheCustomizer
      : Task[Option[FileCache[coursier.util.Task] => FileCache[coursier.util.Task]]] =
    Task.anon { None }

}
object CoursierModule {

  class Resolver(
      repositories: Seq[Repository],
      bind: Dep => BoundDep,
      mapDependencies: Option[Dependency => Dependency] = None,
      customizer: Option[coursier.core.Resolution => coursier.core.Resolution] = None,
      ctx: Option[mill.api.Ctx.Log] = None,
      coursierCacheCustomizer: Option[
        coursier.cache.FileCache[coursier.util.Task] => coursier.cache.FileCache[coursier.util.Task]
      ] = None
  ) {

    def resolveDeps[T: CoursierModule.Resolvable](
        deps: IterableOnce[T],
        sources: Boolean = false
    ): Agg[PathRef] = {
      Lib.resolveDependencies(
        repositories = repositories,
        deps = deps.map(implicitly[CoursierModule.Resolvable[T]].bind(_, bind)),
        sources = sources,
        mapDependencies = mapDependencies,
        customizer = customizer,
        coursierCacheCustomizer = coursierCacheCustomizer,
        ctx = ctx
      ).getOrThrow
    }
  }

  sealed trait Resolvable[T] {
    def bind(t: T, bind: Dep => BoundDep): BoundDep
  }
  implicit case object ResolvableDep extends Resolvable[Dep] {
    def bind(t: Dep, bind: Dep => BoundDep): BoundDep = bind(t)
  }
  implicit case object ResolvableBoundDep extends Resolvable[BoundDep] {
    def bind(t: BoundDep, bind: Dep => BoundDep): BoundDep = t
  }
}
