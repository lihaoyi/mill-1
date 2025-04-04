package mill

import scala.annotation.nowarn

package object scalalib extends mill.scalalib.JsonFormatters {
  implicit class DepSyntax(ctx: StringContext) {
    def ivy(args: Any*): Dep = Dep.parse {
      (
        ctx.parts.take(args.length).zip(args).flatMap { case (p, a) => Seq(p, a) } ++
          ctx.parts.drop(args.length)
      ).mkString
    }
  }

  @nowarn("cat=deprecation")
  type JvmWorkerModule = ZincWorkerModule
  @nowarn("cat=deprecation")
  val JvmWorkerModule = ZincWorkerModule

  @nowarn("cat=deprecation")
  type WithJvmWorker = WithZincWorker
}
