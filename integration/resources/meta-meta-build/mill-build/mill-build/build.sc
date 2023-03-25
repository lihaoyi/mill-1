// build.sc
import mill._, scalalib._

object millbuild extends MillBuildModule {
  def generatedSources = T{
    os.write(
      T.dest / "Constant.scala",
      """package constant
        |object Constant{
        |  def scalatagsVersion = "0.8.2"
        |}
        |""".stripMargin
    )
    super.generatedSources() ++ Seq(PathRef(T.dest / "Constant.scala"))
  }
  def scalaVersion = "2.13.2"
}
