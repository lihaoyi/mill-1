package mill.define

import language.experimental.macros
import scala.reflect.macros.blackbox

object Cross {

  /**
   * A simple cross-module with 1 cross-type [[T1]], which is available in the
   * module body as [[crossValue]]
   */
  trait Module[T1] extends mill.define.Module {
    def crossValue: T1
    def crossValuesList: List[Any] = List(crossValue)
    def wrapperSegments: List[String] = millModuleSegments.parts

    trait CrossValue extends Module[T1]{
      def crossValue: T1 = Module.this.crossValue
      override def wrapperSegments: List[String] = Module.this.millModuleSegments.parts
    }
  }

  /**
   * Convenience trait that can be mixed in to a [[Cross.Module]] to add an
   * additional cross type [[T2]], available in the module body as
   * [[crossValue2]]
   */
  trait Arg2[T2] { this: Module[_] =>
    def crossValue2: T2
    override def crossValuesList: List[Any] = List((crossValue, crossValue2))

    trait CrossValue2 extends Arg2[T2]{  this: Module[_] =>
      def crossValue: T2 = Arg2.this.crossValue2
    }
  }

  /**
   * A cross-module with 2 cross-types [[T1]] and [[T2]], which are available
   * in the module body as [[crossValue]] and [[crossValue2]].
   */
  trait Module2[T1, T2] extends Module[T1] with Arg2[T2]

  /**
   * Convenience trait that can be mixed in to a [[Module2]] to add an
   * additional cross type [[T3]], available in the module body as
   * [[crossValue3]]
   */
  trait Arg3[T3] { this: Module[_] with Arg2[_] =>
    def crossValue3: T3
    override def crossValuesList: List[Any] = List((crossValue, crossValue2, crossValue3))

    trait CrossValue3 extends Arg3[T3]{ this: Module[_] with Arg2[_] =>
      def crossValue: T3 = Arg3.this.crossValue3
    }
  }

  /**
   * A cross-module with 3 cross-types [[T1]] [[T2]] and [[T3]], which are
   * available in the module body as [[crossValue]] [[crossValue2]] and
   * [[crossValue3]].
   */
  trait Module3[T1, T2, T3] extends Module2[T1, T2] with Arg3[T3]

  /**
   * Convenience trait that can be mixed in to a [[Module3]] to add an
   * additional cross type [[T4]], available in the module body as
   * [[crossValue4]]
   */
  trait Arg4[T4] { this: Module[_] with Arg2[_] with Arg3[_] =>
    def crossValue4: T4
    override def crossValuesList: List[Any] =
      List((crossValue, crossValue2, crossValue3, crossValue4))

    trait CrossValue4 extends Arg4[T4] { this: Module[_] with Arg2[_] with Arg3[_] =>
      def crossValue: T4 = Arg4.this.crossValue4
    }
  }

  /**
   * A cross-module with 4 cross-types [[T1]] [[T2]] [[T3]] and [[T4]], which
   * are available in the module body as [[crossValue]] [[crossValue2]]
   * [[crossValue3]] and [[crossValue4]].
   */
  trait Module4[T1, T2, T3, T4] extends Module3[T1, T2, T3] with Arg4[T4]

  /**
   * Convenience trait that can be mixed in to a [[Module5]] to add an
   * additional cross type [[T5]], available in the module body as
   * [[crossValue5]]
   */
  trait Arg5[T5] { this: Module[_] with Arg2[_] with Arg3[_] with Arg4[_] =>
    def crossValue5: T5
    override def crossValuesList: List[Any] =
      List((crossValue, crossValue2, crossValue3, crossValue4, crossValue5))

    trait CrossValue5 extends Arg5[T5]{ this: Module[_] with Arg2[_] with Arg3[_] with Arg4[_] =>
      def crossValue: T5 = Arg5.this.crossValue5
    }
  }

  /**
   * A cross-module with 5 cross-types [[T1]] [[T2]] [[T3]] [[T4]] and [[T5]],
   * which are available in the module body as [[crossValue]] [[crossValue2]]
   * [[crossValue3]] [[crossValue4]] and [[crossValue5]].
   */
  trait Module5[T1, T2, T3, T4, T5] extends Module4[T1, T2, T3, T4] with Arg5[T4]

  /**
   * Convert the given value [[t]] to its cross segments
   */
  def ToSegments[T: ToSegments](t: T): List[String] = implicitly[ToSegments[T]].convert(t)

  /**
   * A type-class defining what types [[T]] are allowed to be used in a
   * cross-module definition
   */
  class ToSegments[-T](val convert: T => List[String])
  object ToSegments {
    implicit object StringToPathSegment extends ToSegments[String](List(_))
    implicit object CharToPathSegment extends ToSegments[Char](v => List(v.toString))
    implicit object LongToPathSegment extends ToSegments[Long](v => List(v.toString))
    implicit object IntToPathSegment extends ToSegments[Int](v => List(v.toString))
    implicit object ShortToPathSegment extends ToSegments[Short](v => List(v.toString))
    implicit object ByteToPathSegment extends ToSegments[Byte](v => List(v.toString))
    implicit object BooleanToPathSegment extends ToSegments[Boolean](v => List(v.toString))
    implicit def SeqToPathSegment[T: ToSegments] = new ToSegments[Seq[T]](
      _.flatMap(implicitly[ToSegments[T]].convert).toList
    )
    implicit def ListToPathSegment[T: ToSegments] = new ToSegments[List[T]](
      _.flatMap(implicitly[ToSegments[T]].convert).toList
    )
  }

  case class Factory[T](
      makeList: Seq[mill.define.Ctx => T],
      crossValuesListLists: Seq[Seq[Any]],
      crossSegmentsList: Seq[Seq[String]],
      crossValuesRaw: Seq[Any]
  )

  object Factory {

    /**
     * Implicitly constructs a Factory[M] for a target-typed `M`. Takes in an
     * expression of type `Any`, but type-checking on the macro- expanded code
     * provides some degree of type-safety.
     */
    implicit def make[M <: Module[_]](t: Any): Factory[M] = macro makeImpl[M]
    def makeImpl[T: c.WeakTypeTag](c: blackbox.Context)(t: c.Expr[Any]): c.Expr[Factory[T]] = {
      import c.universe._
      val tpe = weakTypeOf[T]

      if (!tpe.typeSymbol.isClass) {
        c.abort(c.enclosingPosition, s"Cross type $tpe must be trait")
      }

      if (!tpe.typeSymbol.asClass.isTrait) abortOldStyleClass(c)(tpe)

      val wrappedT = if (t.tree.tpe <:< typeOf[Seq[_]]) t.tree else q"_root_.scala.Seq($t)"
      val v1 = c.freshName(TermName("v1"))
      val ctx0 = c.freshName(TermName("ctx0"))
      val implicitCtx = c.freshName(TermName("implicitCtx"))

      val newTrees = collection.mutable.Buffer.empty[Tree]
      var valuesTree: Tree = null
      var pathSegmentsTree: Tree = null

      val segments = q"_root_.mill.define.Cross.ToSegments"
      if (tpe <:< typeOf[Module[_]]) {
        newTrees.append(q"override def crossValue = $v1")
        pathSegmentsTree = q"$segments($v1)"
        valuesTree = q"$wrappedT.map(List(_))"
      } else c.abort(
        c.enclosingPosition,
        s"Cross type $tpe must implement Cross.Module[T]"
      )

      if (tpe <:< typeOf[Arg2[_]]) {
        // For `Module2` and above, `crossValue` is no longer the entire value,
        // but instead is just the first element of a tuple
        newTrees.clear()
        newTrees.append(q"override def crossValue = $v1._1")
        newTrees.append(q"override def crossValue2 = $v1._2")
        pathSegmentsTree = q"$segments($v1._1) ++ $segments($v1._2)"
        valuesTree = q"$wrappedT.map(_.productIterator.toList)"
      }

      if (tpe <:< typeOf[Arg3[_]]) {
        newTrees.append(q"override def crossValue3 = $v1._3")
        pathSegmentsTree = q"$segments($v1._1) ++ $segments($v1._2) ++ $segments($v1._3)"
      }

      if (tpe <:< typeOf[Arg4[_]]) {
        newTrees.append(q"override def crossValue4 = $v1._4")
        pathSegmentsTree =
          q"$segments($v1._1) ++ $segments($v1._2) ++ $segments($v1._3) ++ $segments($v1._4)"
      }

      if (tpe <:< typeOf[Arg5[_]]) {
        newTrees.append(q"override def crossValue5 = $v1._5")
        pathSegmentsTree =
          q"$segments($v1._1) ++ $segments($v1._2) ++ $segments($v1._3) ++ $segments($v1._4) ++ $segments($v1._5)"
      }

      val tree = q"""
        mill.define.Cross.Factory[$tpe](
          makeList = $wrappedT.map(($v1: ${tq""}) =>
            ($ctx0: ${tq""}) => {
              implicit val $implicitCtx = $ctx0
              new $tpe{..$newTrees}
            }
          ),
          crossSegmentsList = $wrappedT.map(($v1: ${tq""}) => $pathSegmentsTree ),
          crossValuesListLists = $valuesTree,
          crossValuesRaw = $wrappedT
       ).asInstanceOf[${weakTypeOf[Factory[T]]}]
      """

      c.Expr[Factory[T]](tree)
    }

    def abortOldStyleClass(c: blackbox.Context)(tpe: c.Type) = {
      val primaryConstructorArgs =
        tpe.typeSymbol.asClass.primaryConstructor.typeSignature.paramLists.head

      val oldArgStr = primaryConstructorArgs
        .map { s => s"${s.name}: ${s.typeSignature}" }
        .mkString(", ")

      def parenWrap(s: String) =
        if (primaryConstructorArgs.size == 1) s
        else s"($s)"

      val newTypeStr = primaryConstructorArgs.map(_.typeSignature.toString).mkString(", ")
      val newForwarderStr = primaryConstructorArgs.map(_.name.toString).mkString(", ")

      c.abort(
        c.enclosingPosition,
        s"""
           |Cross type ${tpe.typeSymbol.name} must be trait, not a class. Please change:
           |
           |  class ${tpe.typeSymbol.name}($oldArgStr)
           |
           |To:
           |
           |  trait ${tpe.typeSymbol.name} extends Cross.Module[${parenWrap(newTypeStr)}]{
           |    val ${parenWrap(newForwarderStr)} = crossValue
           |  }
           |
           |You also no longer use `: _*` when instantiating a cross-module:
           |
           |  Cross[${tpe.typeSymbol.name}](values:_*)
           |
           |Instead, you can pass the sequence directly:
           |
           |  Cross[${tpe.typeSymbol.name}](values)
           |
           |Note that the `millSourcePath` of cross modules has changed in
           |Mill 0.11.0, and no longer includes the cross values by default.
           |If you have `def millSourcePath = super.millSourcePath / os.up`,
           |you may remove it. If you do not have this definition, you can
           |preserve the old behavior via `def millSourcePath = super.millSourcePath / crossValue`
           |
           |""".stripMargin
      )
    }
  }

  trait Resolver[-T <: Cross.Module[_]] {
    def resolve[V <: T](c: Cross[V]): V
  }
}

/**
 * Models "cross-builds": sets of duplicate builds which differ only in the
 * value of one or more "case" variables whose values are determined at runtime.
 * Used via:
 *
 * object foo extends Cross[FooModule]("bar", "baz", "qux")
 * class FooModule extends Module{
 *   ... crossValue ...
 * }
 */
class Cross[M <: Cross.Module[_]](factories: Cross.Factory[M]*)(implicit ctx: mill.define.Ctx)
    extends mill.define.Module()(ctx) {

  private val items: List[(List[Any], List[String], M)] = for {
    factory <- factories.toList
    (crossSegments, (crossValues, make)) <-
      factory.crossSegmentsList.zip(factory.crossValuesListLists.zip(factory.makeList))
  } yield {
    val relPath = ctx.segment.pathSegments
    val sub = make(
      ctx
        .withSegments(ctx.segments ++ Seq(ctx.segment))
        .withMillSourcePath(ctx.millSourcePath / relPath)
        .withSegment(Segment.Cross(crossSegments))
        .withCrossValues(factories.flatMap(_.crossValuesRaw))
    )

    (crossValues.toList, crossSegments.toList, sub)
  }

  override lazy val millModuleDirectChildren: Seq[Module] =
    super.millModuleDirectChildren ++ crossModules

  /**
   * A list of the cross modules, in
   * the order the original cross values were given in
   */
  def crossModules: List[M] = items.collect { case (_, _, v) => v }

  /**
   * A mapping of the raw cross values to the cross modules, in
   * the order the original cross values were given in
   */
  val valuesToModules: collection.Map[List[Any], M] =
    items.map { case (values, segments, subs) => (values, subs) }.to(
      collection.mutable.LinkedHashMap
    )

  /**
   * A mapping of the string-ified string segments to the cross modules, in
   * the order the original cross values were given in
   */
  val segmentsToModules: collection.Map[List[String], M] =
    items.map { case (values, segments, subs) => (segments, subs) }.to(
      collection.mutable.LinkedHashMap
    )

  /**
   * Fetch the cross module corresponding to the given cross values
   */
  def get(args: Seq[Any]): M = valuesToModules(args.toList)

  /**
   * Fetch the cross module corresponding to the given cross values
   */
  def apply(arg0: Any, args: Any*): M = {
    valuesToModules(arg0 :: args.toList)
  }

  /**
   * Fetch the relevant cross module given the implicit resolver you have in
   * scope. This is often the first cross module whose cross-version is
   * compatible with the current module.
   */
  def apply[V >: M <: Cross.Module[_]]()(implicit resolver: Cross.Resolver[V]): M = {
    resolver.resolve(this.asInstanceOf[Cross[V]]).asInstanceOf[M]
  }
}
