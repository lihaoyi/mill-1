package mill.integration

import mill.testkit.IntegrationTestSuite

import mill.testkit.IntegrationTestSuite
import utest._

object MillInitTests extends IntegrationTestSuite {

  def tests: Tests = Tests {
    test("Mill init works") {
      val workspacePath = initWorkspace()
      eval(("init", "com-lihaoyi/mill-scala-hello.g8", "--name=example")) ==> true
      val projFile = workspacePath / "example" / "build.sc"
      assert(os.exists(projFile))
    }
  }
}
