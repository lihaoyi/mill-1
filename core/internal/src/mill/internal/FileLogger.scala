package mill.internal

import mill.api.{Logger, SystemStreams}

import java.io.{OutputStream, PrintStream}
import java.nio.file.{Files, StandardOpenOption}

private[mill] class FileLogger(
    override val colored: Boolean,
    file: os.Path,
    override val debugEnabled: Boolean,
    append: Boolean = false
) extends Logger with AutoCloseable {
  override def toString: String = s"FileLogger($file)"
  private var outputStreamUsed: Boolean = false

  lazy val fileStream: PrintStream = {

    val options = Seq(
      Seq(StandardOpenOption.CREATE, StandardOpenOption.WRITE),
      Seq(StandardOpenOption.APPEND).filter(_ => append),
      Seq(StandardOpenOption.TRUNCATE_EXISTING).filter(_ => !append && !outputStreamUsed)
    ).flatten
    //    if (!append && !outputStreamUsed) os.remove.all(file)
    outputStreamUsed = true
    var folderCreated = false
    // Lazily create the folder and file that we're logging to, so as to avoid spamming the out/
    // folder with empty folders/files for the vast majority of tasks that do not have any logs
    lazy val inner = os.checker.withValue(os.Checker.Nop) {
      if (!os.exists(file / os.up)) os.makeDir.all(file / os.up)
      folderCreated = true
      Files.newOutputStream(file.toNIO, options*)
    }
    new PrintStream(new OutputStream {
      override def write(b: Int): Unit = inner.write(b)

      override def write(b: Array[Byte]): Unit = inner.write(b)

      override def write(b: Array[Byte], off: Int, len: Int): Unit = inner.write(b, off, len)

      override def close(): Unit = if (folderCreated) inner.close()

      override def flush(): Unit = if (folderCreated) inner.flush()
    })
  }

  val streams = new SystemStreams(fileStream, fileStream, mill.api.DummyInputStream)
  def info(s: String): Unit = streams.out.println(s)
  def error(s: String): Unit = streams.out.println(s)
  def ticker(s: String): Unit = streams.out.println(s)
  def debug(s: String): Unit = if (debugEnabled) streams.out.println(s)
  def close(): Unit = {
    if (outputStreamUsed)
      streams.out.close()
  }
  override def subLogger(path: os.Path, verboseKeySuffix: String, message: String): Logger = {
    new FileLogger(colored, path, debugEnabled, append)
  }
}
