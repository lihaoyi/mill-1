//// SNIPPET:BUILD
import mill._, javalib._

object `package` extends RootModule with JavaModule {
  def mainClass = Some("foo.Qux")
}
