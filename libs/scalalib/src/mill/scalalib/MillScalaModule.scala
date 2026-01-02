package mill.scalalib

package millbuild

trait MillScalaModule extends mill.scalalib.ScalaModule with MillJavaModule {
  def foo = "bar222222"
}
