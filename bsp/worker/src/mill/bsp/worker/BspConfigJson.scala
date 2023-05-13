package mill.bsp.worker

import ch.epfl.scala.bsp4j.BspConnectionDetails
import upickle.default._

import scala.jdk.CollectionConverters._

private case class BspConfigJson(
    name: String,
    argv: Seq[String],
    millVersion: String,
    bspVersion: String,
    languages: Seq[String]
) extends BspConnectionDetails(name, argv.asJava, millVersion, bspVersion, languages.asJava)

private object BspConfigJson {
  implicit val rw: ReadWriter[BspConfigJson] = macroRW
}
