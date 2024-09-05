package mill.integration

import mill.testkit.IntegrationTestSuite

import utest._

object HygieneTests extends IntegrationTestSuite {
  val tests: Tests = Tests {
    test - integrationTest { tester => import tester._
      val res = eval("scala.foo")
      assert(res.isSuccess == true)
      val output = out("scala.foo").text
      assert(output.contains("\"fooValue\""))
    }
  }
}
