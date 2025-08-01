package mill.api

import mill.api.daemon.internal.{ModuleApi, internal}
import mill.api.internal.{OverrideMapping, Reflect}
import mill.api.Task.Simple

import scala.reflect.ClassTag

/**
 * Represents a namespace within the Mill build hierarchy, containing nested
 * modules or tasks.
 *
 * `Module` is a class meant to be extended by ``trait``s *only*, in order to
 * propagate the implicit parameters forward to the final concrete
 * instantiation site so they can capture the enclosing/line information of
 * the concrete instance.
 */
trait Module extends Module.BaseClass with ModuleCtx.Wrapper with ModuleApi {
  protected implicit def moduleNestedCtx: ModuleCtx.Nested = moduleCtx
    .withMillSourcePath(moduleDir)
    .withSegments(moduleSegments)
    .withEnclosingModule(this)

  /**
   * Miscellaneous machinery around traversing & querying the build hierarchy,
   * that should not be needed by normal users of Mill
   */
  @internal
  object moduleInternal extends Module.Internal(this)

  def moduleDirectChildren: Seq[Module] = millModuleDirectChildrenImpl

  // We keep a private `lazy val` and a public `def` so
  // subclasses can call `super.millModuleDirectChildren`
  private lazy val millModuleDirectChildrenImpl: Seq[Module] =
    moduleInternal.reflectNestedObjects[Module]().toSeq

  def moduleDir: os.Path = moduleCtx.millSourcePath
  private[mill] def moduleDirJava = moduleDir.toNIO

  def moduleSegments: Segments = moduleCtx.segments

  override def toString = moduleSegments.render

  private[mill] val moduleLinearized: Seq[Class[?]] =
    OverrideMapping.computeLinearization(this.getClass)
}

object Module {

  /**
   * Base class of the [[Module]] trait, allowing us to take implicit arguments
   * (traits cannot). Cannot be used directly, because traits inheriting from
   * classes results in the class being invisible to java reflection, which
   * messes up the module discovery process
   */
  @internal
  class BaseClass(implicit outerCtx0: mill.api.ModuleCtx) extends mill.api.internal.Cacher {
    def moduleCtx = outerCtx0
  }

  @internal
  class Internal(outer: Module) {
    def traverse[T](f: Module => Seq[T]): Seq[T] = {
      def rec(m: Module): Seq[T] = f(m) ++ m.moduleDirectChildren.flatMap(rec)
      rec(outer)
    }

    lazy val modules: Seq[Module] = traverse(Seq(_))
    lazy val segmentsToModules: Map[Segments, Module] =
      modules.map(m => (m.moduleSegments, m)).toMap

    lazy val simpleTasks: Set[Simple[?]] =
      traverse { _.moduleInternal.reflectAll[Simple[?]].toIndexedSeq }.toSet

    def reflect[T: ClassTag](filter: String => Boolean): Seq[T] = {
      Reflect.reflect(
        outer.getClass,
        implicitly[ClassTag[T]].runtimeClass,
        filter,
        noParams = true,
        (cls, noParams, inner) =>
          Reflect.getMethods(cls, noParams, inner, scala.reflect.NameTransformer.decode)
      )
        .map(_.invoke(outer).asInstanceOf[T])
        .toSeq
    }

    def reflectAll[T: ClassTag]: Seq[T] = reflect[T](Function.const(true))

    def reflectNestedObjects[T: ClassTag](filter: String => Boolean = Function.const(true))
        : Seq[T] = {
      Reflect.reflectNestedObjects02(
        outer.getClass,
        filter,
        (cls, noParams, inner) =>
          Reflect.getMethods(cls, noParams, inner, scala.reflect.NameTransformer.decode)
      )
        .map { case (getter = getter) => getter(outer) }
        .toSeq
    }
  }
}
