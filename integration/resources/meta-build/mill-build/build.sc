import mill._, scalalib._

object millbuild extends MillBuildModule{
  def ivyDeps = Agg(ivy"com.lihaoyi::scalatags:0.8.2")
}