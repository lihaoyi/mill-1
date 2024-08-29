//// SNIPPET:BUILD
import mill._, javalib._

object build extends RootModule with JavaModule{
  def forkArgs = Seq("-Xmx4g", "-Dmy.jvm.property=hello")
  def forkEnv = Map("MY_ENV_VAR" -> "WORLD")
}

//// SNIPPET:END