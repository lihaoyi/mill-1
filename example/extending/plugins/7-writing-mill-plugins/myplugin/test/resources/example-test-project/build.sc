import $ivy.`com.lihaoyi::myplugin:0.0.1`
import mill._, myplugin._

object build extends RootModule with LineCountJavaModule{
  def lineCountResourceFileName = "line-count.txt"
}

/** Usage

> ./mill run
Line Count: 17
...

> printf "\n" >> src/foo/Foo.java

> ./mill run
Line Count: 18
...

*/