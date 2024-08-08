import mill._

trait MyModule extends Module{
  def crossValue: String
  def name: T[String]
  def param = Task { name() + " Param Value: " + crossValue }
}

object foo extends Cross[FooModule]("a", "b")
trait FooModule extends Cross.Module[String] {
  object bar extends MyModule with CrossValue{
    def name = "Bar"
  }
  object qux extends MyModule with CrossValue{
    def name = "Qux"
  }
}

def baz = Task { s"hello ${foo("a").bar.param()}" }

// [graphviz]
// ....
// digraph G {
//   node [shape=box width=0 height=0 style=filled fillcolor=white]
//   "root-module" [style=dashed]
//   foo [style=dashed]
//   "foo[a]" [style=dashed]
//   "foo[b]" [style=dashed]
//   "foo[a].bar" [style=dashed]
//   "foo[a].qux" [style=dashed]
//   "foo[b].bar" [style=dashed]
//   "foo[b].qux" [style=dashed]
//   "root-module" -> foo -> "foo[a]" [style=dashed]
//   foo -> "foo[b]"  [style=dashed]
//   "foo[a]" -> "foo[a].bar" [style=dashed]
//   "foo[a]" -> "foo[a].qux" [style=dashed]
//   "foo[b]" -> "foo[b].bar" [style=dashed]
//   "foo[b]" -> "foo[b].qux" [style=dashed]
//   "foo[a].bar" -> "foo[a].bar.name" [style=dashed]
//   "foo[a].bar" -> "foo[a].bar.param" [style=dashed]
//   "foo[a].qux" -> "..." [style=invis]
//   "foo[b].bar" -> "..." [style=invis]
//   "foo[b].qux" -> "..." [style=invis]
//   "..." [color=white]
//   "foo[a].bar.name" -> "foo[a].bar.param" [constraint=false]
// }
// ....
//
// You can use the `CrossValue` trait within any `Cross.Module` to
// propagate the `crossValue` defined by an enclosing `Cross.Module` to some
// nested module. In this case, we use it to bind `crossValue` so it can be
// used in `def param`. This lets you reduce verbosity by defining the `Cross`
// once for a group of modules rather than once for every single module  in
// that group. There are corresponding `InnerCrossModuleN` traits for cross
// modules that take multiple inputs.
//
// You can reference the modules and tasks defined within such a
// `CrossValue` as is done in `def qux` above

/** Usage

> mill show foo[a].bar.param
"Bar Param Value: a"

> mill show foo[b].qux.param
"Qux Param Value: b"

> mill show baz
"hello Bar Param Value: a"

*/