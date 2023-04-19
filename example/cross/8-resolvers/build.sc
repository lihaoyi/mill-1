import mill._

import mill._

trait MyModule extends Cross.Module[String] {
  implicit object resolver extends mill.define.Cross.Resolver[MyModule] {
    def resolve[V <: MyModule](c: Cross[V]): V = c.valuesToModules(List(crossValue))
  }
}

object foo extends mill.Cross[FooModule]("2.10", "2.11", "2.12")
trait FooModule extends MyModule {
  def suffix = T { "_" + crossValue }
}

object bar extends mill.Cross[BarModule]("2.10", "2.11", "2.12")
trait BarModule extends MyModule {
  def bigSuffix = T { "[[[" + foo().suffix() + "]]]" }
}

// You can define an implicit `mill.define.Cross.Resolver` within your
// cross-modules, which would let you use a shorthand `foo()` syntax when
// referring to other cross-modules with an identical set of cross values.
//
// While the example `resolver` simply looks up the target `Cross` value for
// the cross-module instance with the same `crossVersion`, you can make the
// resolver arbitrarily complex. E.g. the `resolver` for
// `mill.scalalib.CrossScalaModule` looks for a cross-module instance whose
// `scalaVersion` is binary compatible (e.g. 2.10.5 is compatible with 2.10.3)
// with the current cross-module.

/* Example Usage

> ./mill show bar[2.10].bigSuffix
[[[_2.10]]]
*/