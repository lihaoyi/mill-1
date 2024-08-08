// Mill handles cross-building of all sorts via the `Cross[T]` module.

import mill._

object foo extends Cross[FooModule]("2.10", "2.11", "2.12")
trait FooModule extends Cross.Module[String] {
  def suffix = task { "_" + crossValue }
  def bigSuffix = task { "[[[" + suffix() + "]]]" }
  def sources = task.sources(millSourcePath)
}

// [graphviz]
// ....
// digraph G {
//   rankdir=LR
//   node [shape=box width=0 height=0 style=filled fillcolor=white]

//   subgraph cluster_2 {
//     label="foo[2.12]"
//     style=dashed
//     "foo[2.12].suffix" -> "foo[2.12].bigSuffix"
//     "foo[2.12].sources"
//   }
//   subgraph cluster_1 {
//     label="foo[2.11]"
//     style=dashed
//     "foo[2.11].suffix" -> "foo[2.11].bigSuffix"
//     "foo[2.11].sources"
//   }
//   subgraph cluster_0 {
//     label="foo[2.10]"
//     style=dashed
//     "foo[2.10].suffix" -> "foo[2.10].bigSuffix"
//     "foo[2.10].sources"
//   }
// }
// ....

// Cross modules defined using the `Cross[T]` class allow you to define
// multiple copies of the same module, differing only in some input key. This
// is very useful for building the same module against different versions of a
// language or library, or creating modules to represent folders on the
// filesystem.
//
// This example defines three copies of `FooModule`: `"2.10"`, `"2.11"` and
// `"2.12"`, each of which has their own `suffix` target. You can then run
// them as shown below. Note that by default, `sources` returns `foo` for every
// cross module, assuming you want to build the same sources for each. This can
// be overridden.

/** Usage

> mill show foo[2.10].suffix
"_2.10"

> mill show foo[2.10].bigSuffix
"[[[_2.10]]]"

> mill show foo[2.10].sources
[
  ".../foo"
]

> mill show foo[2.12].suffix
"_2.12"

> mill show foo[2.12].bigSuffix
"[[[_2.12]]]"

> mill show foo[2.12].sources
[
  ".../foo"
]

*/
