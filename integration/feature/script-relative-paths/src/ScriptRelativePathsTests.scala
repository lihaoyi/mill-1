package mill.integration

import mill.testkit.UtestIntegrationTestSuite
import utest.*

object ScriptRelativePathsTests extends UtestIntegrationTestSuite {
  val tests: Tests = Tests {
    test("cachedTaskDestScriptRunsFromModuleDir") - integrationTest { tester =>
      val generated = tester.eval("Scripts.myScriptPath")
      val run = tester.eval("Scripts.runScript")

      assert(
        generated.isSuccess,
        run.isSuccess,
        run.out.contains("hi from Script"),
        run.out.contains("hi from Mill")
      )
    }
  }
}
