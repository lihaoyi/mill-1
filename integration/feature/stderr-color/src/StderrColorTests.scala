package mill.integration

import mill.testkit.UtestIntegrationTestSuite
import utest.*

object StderrColorTests extends UtestIntegrationTestSuite {
  private val AnsiEscape = 0x1b.toByte
  private val BluePrefix = "\u001b[34m"
  private val YellowWarning = "\u001b[33mwarn"

  val tests: Tests = Tests {
    test("automaticColorFollowsEachStream") {
      if (!scala.util.Properties.isWin) {
        integrationTest { tester =>
          for (daemon <- Seq(true, false)) {
            checkStreams(
              tester,
              daemon = daemon,
              stderrIsTerminal = true,
              expectedStderrColor = true
            )
            checkStreams(
              tester,
              daemon = daemon,
              stderrIsTerminal = false,
              expectedStderrColor = false
            )
          }
        }
      }
    }

    test("automaticPrefixColorFollowsStdout") {
      if (!scala.util.Properties.isWin) {
        integrationTest { tester =>
          for (daemon <- Seq(true, false)) {
            checkStreams(
              tester,
              daemon = daemon,
              stderrIsTerminal = true,
              expectedStderrColor = true,
              ticker = true,
              expectedStdoutPrefixColor = false
            )
            checkStreams(
              tester,
              daemon = daemon,
              stderrIsTerminal = false,
              expectedStderrColor = false,
              ticker = true,
              expectedStdoutPrefixColor = true
            )
          }
        }
      }
    }

    test("explicitColorOverridesRemainGlobal") {
      if (!scala.util.Properties.isWin) {
        integrationTest { tester =>
          checkStreams(
            tester,
            daemon = true,
            stderrIsTerminal = false,
            expectedStderrColor = true,
            millArgs = Seq("--color=true")
          )
          checkStreams(
            tester,
            daemon = true,
            stderrIsTerminal = true,
            expectedStderrColor = false,
            millArgs = Seq("--color=false")
          )
          checkStreams(
            tester,
            daemon = true,
            stderrIsTerminal = false,
            expectedStderrColor = true,
            env = Map("FORCE_COLOR" -> "1")
          )
          checkStreams(
            tester,
            daemon = true,
            stderrIsTerminal = true,
            expectedStderrColor = false,
            env = Map("NO_COLOR" -> "1")
          )
        }
      }
    }
  }

  private def checkStreams(
      tester: mill.testkit.IntegrationTester,
      daemon: Boolean,
      stderrIsTerminal: Boolean,
      expectedStderrColor: Boolean,
      millArgs: Seq[String] = Seq.empty,
      env: Map[String, String] = Map.empty,
      ticker: Boolean = false,
      expectedStdoutPrefixColor: Boolean = false
  ): Unit = {
    val prepared = tester.proc(millArgs ++ Seq("emitMarkers"))
    val baseCommand0 = prepared.cmd.value.iterator.map(_.toString).toVector
      .filterNot(_ == "--no-daemon")
    val tickerIndex = baseCommand0.indexOf("--ticker")
    val baseCommand = baseCommand0.updated(tickerIndex + 1, ticker.toString)
    val command =
      if (daemon) baseCommand
      else baseCommand.patch(1, Seq("--no-daemon"), 0)

    val transcript = os.temp(prefix = s"mill-terminal-$daemon-", suffix = ".log")
    val redirected = os.temp(prefix = s"mill-redirected-$daemon-", suffix = ".log")
    val wrapper = os.temp(prefix = s"mill-stderr-color-$daemon-", suffix = ".sh")
    val redirect = if (stderrIsTerminal) ">" else "2>"
    os.write.over(
      wrapper,
      s"#!/bin/sh\n${command.map(shellQuote).mkString(" ")} $redirect${shellQuote(redirected.toString)}\n"
    )
    os.perms.set(wrapper, "rwxr-xr-x")

    val scriptCommand =
      if (scala.util.Properties.isMac)
        Seq("script", "-q", transcript.toString, wrapper.toString)
      else
        Seq("script", "-q", "-e", "-c", wrapper.toString, transcript.toString)

    val result = os.proc(scriptCommand).call(
      cwd = tester.workspacePath,
      env = (prepared.env - "NO_COLOR" - "FORCE_COLOR") ++ env +
        ("TERM" -> "xterm-256color"),
      stdout = os.Pipe,
      stderr = os.Pipe,
      check = false,
      propagateEnv = false
    )
    val terminalOutput = os.read.bytes(transcript)
    val redirectedOutput = os.read.bytes(redirected)
    val stdoutOutput = if (stderrIsTerminal) redirectedOutput else terminalOutput
    val stderrOutput = if (stderrIsTerminal) terminalOutput else redirectedOutput
    val stderrText = String(stderrOutput)

    assert(result.exitCode == 0)
    assert(String(stdoutOutput).contains("STDOUT_MARKER"))
    assert(String(stdoutOutput).contains(BluePrefix) == expectedStdoutPrefixColor)
    if (!expectedStdoutPrefixColor) assert(!stdoutOutput.contains(AnsiEscape))
    assert(stderrText.contains("STDERR_COLOR_MARKER"))
    assert(stderrText.contains(YellowWarning) == expectedStderrColor)
    if (!expectedStderrColor) assert(!stderrOutput.contains(AnsiEscape))
  }

  private def shellQuote(value: String): String =
    "'" + value.replace("'", "'\"'\"'") + "'"
}
