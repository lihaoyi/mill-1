import mill._, scalalib._

val moduleNames = interp.watchValue(os.list(millSourcePath / "modules").map(_.last))

object modules extends Cross[FolderModule](moduleNames:_*)
trait FolderModule extends ScalaModule{
  def millSourcePath = super.millSourcePath / millCrossValue
  def scalaVersion = "2.13.2"
}

// It is sometimes necessary for the instances of a cross-module to vary based
// on some kind of runtime information: perhaps the list of modules is stored
// in some config file, or is inferred based on the folders present on the
// filesystem.
//
// In those cases, you can write arbitrary code to populate the cross-module
// cases, as long as you wrap the value in a `interp.watchValue`. This ensures
// that Mill is aware that the module structure depends on that value, and will
// re-compute the value and re-create the module structure if the value changes.

/* Example Usage

> ./mill resolve modules[_]
modules[bar]
modules[foo]
modules[qux]

> ./mill modules[bar].run
Hello World Bar

> ./mill modules[new].run
error: Cannot resolve modules[new]

> cp -r modules/bar modules/new

> sed -i 's/Bar/New/g' modules/new/src/Example.scala

> ./mill resolve modules[_]
modules[bar]
modules[foo]
modules[qux]
modules[new]

> ./mill modules[new].run
Hello World New

*/