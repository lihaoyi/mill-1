package mill.playlib

import mill.scalalib._
import mill.Task

private[playlib] trait Server extends ScalaModule with Version {

  def nettyServer = Task { component("play-netty-server")() }

  def akkaHttpServer = Task { component("play-akka-http-server")() }

  def pekkoHttpServer = Task { component("play-pekko-http-server")() }

  def playServerProvider = Task {
    if (playVersion().startsWith("2."))
      akkaHttpServer()
    else
      pekkoHttpServer()
  }

  override def runLibraryDeps = Task {
    super.runLibraryDeps() ++ Seq(playServerProvider())
  }

  override def mainClass = Task { Some("play.core.server.ProdServerStart") }
}
