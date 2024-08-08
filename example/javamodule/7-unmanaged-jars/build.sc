//// SNIPPET:BUILD
import mill._, javalib._

object foo extends RootModule with JavaModule {
  def unmanagedClasspath = Task {
    if (!os.exists(millSourcePath / "lib")) Agg()
    else Agg.from(os.list(millSourcePath / "lib").map(PathRef(_)))
  }
}
