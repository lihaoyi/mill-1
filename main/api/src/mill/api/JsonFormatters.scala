package mill.api

import upickle.default.{ReadWriter => RW}

import scala.reflect.ClassTag
import scala.util.matching.Regex

object JsonFormatters extends JsonFormatters

/**
 * Defines various default JSON formatters used in mill.
 */
trait JsonFormatters {
  def pathToString(path: os.Path) =
    if (path.startsWith(os.pwd)) path.relativeTo(os.pwd).toString()
    else path.toString()

  def stringToPath(s: String) = os.FilePath(s) match {
    case p: os.Path => p
    case s: os.SubPath => os.pwd / s
    case r: os.RelPath => os.pwd / r
  }
  implicit val pathReadWrite: RW[os.Path] = upickle.default.readwriter[String]
    .bimap[os.Path](pathToString, stringToPath)

  implicit val regexReadWrite: RW[Regex] = upickle.default.readwriter[String]
    .bimap[Regex](
      _.pattern.toString,
      _.r
    )

  implicit val bytesReadWrite: RW[geny.Bytes] = upickle.default.readwriter[String]
    .bimap(
      o => java.util.Base64.getEncoder.encodeToString(o.array),
      str => new geny.Bytes(java.util.Base64.getDecoder.decode(str))
    )

  implicit lazy val crFormat: RW[os.CommandResult] = upickle.default.macroRW

  implicit val stackTraceRW: RW[StackTraceElement] =
    upickle.default.readwriter[ujson.Obj].bimap[StackTraceElement](
      ste =>
        ujson.Obj(
          "declaringClass" -> ujson.Str(ste.getClassName),
          "methodName" -> ujson.Str(ste.getMethodName),
          "fileName" -> ujson.Arr(Option(ste.getFileName()).map(ujson.Str(_)).toSeq: _*),
          "lineNumber" -> ujson.Num(ste.getLineNumber)
        ),
      json =>
        new StackTraceElement(
          json("declaringClass").str.toString,
          json("methodName").str.toString,
          json("fileName").arr.headOption.map(_.str.toString).orNull,
          json("lineNumber").num.toInt
        )
    )

  implicit def enumFormat[T <: java.lang.Enum[_]: ClassTag]: RW[T] =
    upickle.default.readwriter[String].bimap(
      _.name(),
      (s: String) =>
        implicitly[ClassTag[T]]
          .runtimeClass
          .getConstructor(classOf[String])
          .newInstance(s)
          .asInstanceOf[T]
    )
}
