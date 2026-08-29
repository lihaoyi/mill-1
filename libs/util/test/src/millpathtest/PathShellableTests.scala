package millpathtest

import mill.*
import mill.api.JsonFormatters.pathReadWrite
import utest.*

object PathShellableTests extends TestSuite {
  val tests: Tests = Tests {
    test("runsFromDifferentCwdButSerializesRelatively") {
      val scriptDir = os.temp.dir()
      val subprocessCwd = os.temp.dir()
      try {
        val script = scriptDir / (if (scala.util.Properties.isWin) "script.cmd" else "script.sh")
        val scriptContents =
          if (scala.util.Properties.isWin) "@echo off\r\necho shell path ok\r\n"
          else "#!/bin/sh\necho shell path ok\n"
        os.write(script, scriptContents, perms = "rwxr-xr-x")
        val serializer = os.Path.pathRemapSerializerNio(
          Seq(scriptDir.wrapped -> java.nio.file.Paths.get("..", "mill-workspace"))
        )

        os.Path.pathSerializer.withValue(serializer) {
          val serializedPath = script.toString
          val serializedJson = upickle.write(script)
          val command =
            if (scala.util.Properties.isWin) os.proc("cmd.exe", "/c", script)
            else os.proc(script)
          val result = command.call(cwd = subprocessCwd)

          assert(
            serializedPath.startsWith(".."),
            upickle.read[String](serializedJson) == serializedPath,
            upickle.read[os.Path](serializedJson) == script,
            command.commandChunks.contains(PathRef.toAbsString(script)),
            result.out.trim() == "shell path ok"
          )
        }
      } finally {
        os.remove.all(scriptDir)
        os.remove.all(subprocessCwd)
      }
    }
  }
}
