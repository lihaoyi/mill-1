import $ivy.`com.lihaoyi::myplugin:0.0.1`
import mill._, myplugin._

object `package` extends RootModule with LineCountJavaModule{
  def lineCountResourceFileName = "line-count.txt"
}

