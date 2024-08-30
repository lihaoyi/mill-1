//// SNIPPET:BUILD
import mill._, scalalib._

object `package` extends RootModule with ScalaModule {
  def scalaVersion = "2.13.8"
  def mainClass = Some("foo.Qux")
}

//// SNIPPET:END

// Mill's `foo.run` by default will discover which main class to run from your
// compilation output, but if there is more than one or the main class comes from
// some library you can explicitly specify which one to use. This also adds the
// main class to your `foo.jar` and `foo.assembly` jars.

/** Usage

> ./mill run
Hello Qux

*/