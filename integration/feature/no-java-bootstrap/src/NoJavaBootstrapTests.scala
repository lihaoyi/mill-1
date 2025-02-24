package mill.integration

import mill.testkit.UtestIntegrationTestSuite

import utest._

object NoJavaBootstrapTests extends UtestIntegrationTestSuite {
  override def propagateJavaHome = false
  val tests: Tests = Tests {
    test - integrationTest { tester =>
      import tester._
      os.remove(tester.workspacePath / ".mill-jvm-version")

      // The Mill server process should use the default Mill Java version,
      // even without the `.mill-jvm-version` present
      val res1 = eval("foo", env = Map("MILL_TEST_SUITE_IGNORE_SYSTEM_JAVA" -> "true"))
      assert(res1.out == System.getProperty("java.version"))

      // Any `JavaModule`s run from the Mill server should also inherit
      // the default Mill Java version from it
      val res2 = eval("bar.run", env = Map("MILL_TEST_SUITE_IGNORE_SYSTEM_JAVA" -> "true"))
      assert(res2.out == s"Hello World! ${System.getProperty("java.version")}")
    }
  }
}
