package mill.launcher

import utest.*

object MillProcessLauncherTests extends TestSuite {
  def tests: Tests = Tests {
    test("stdoutAndStderrAreDetectedIndependently") {
      test("stdoutRedirected") {
        val onlyStderrIsTerminal = (fileDescriptor: Int) => if (fileDescriptor == 2) 1 else 0

        assert(!MillProcessLauncher.isTerminal(1, onlyStderrIsTerminal))
        assert(MillProcessLauncher.isTerminal(2, onlyStderrIsTerminal))
      }

      test("stderrRedirected") {
        val onlyStdoutIsTerminal = (fileDescriptor: Int) => if (fileDescriptor == 1) 1 else 0

        assert(MillProcessLauncher.isTerminal(1, onlyStdoutIsTerminal))
        assert(!MillProcessLauncher.isTerminal(2, onlyStdoutIsTerminal))
      }
    }

    test("windowsAnsiSetupSelectsOnlyTerminalStreams") {
      assert(MillProcessLauncher.terminalFileDescriptors(false, false) == Seq.empty)
      assert(MillProcessLauncher.terminalFileDescriptors(true, false) == Seq(1))
      assert(MillProcessLauncher.terminalFileDescriptors(false, true) == Seq(2))
      assert(MillProcessLauncher.terminalFileDescriptors(true, true) == Seq(1, 2))
    }
  }
}
