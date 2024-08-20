package mill.integration

import mill.testkit.IntegrationTestSuite

import utest._

object ImportIvyWorkerInvalidation extends IntegrationTestSuite {

  val tests: Tests = Tests {
    test {
      val wsRoot = initWorkspace()
      assert(eval("app.compile").isSuccess)
      mangleFile(wsRoot / "build.sc", _.replace("object app", "println(\"hello\"); object app"))
      assert(eval("app.compile").isSuccess)
    }
  }
}
