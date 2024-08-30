package mill.integration

import mill.testkit.IntegrationTestSuite

import utest._

object InvalidPackageDeclaration extends IntegrationTestSuite {
  val tests: Tests = Tests {
    initWorkspace()

    test("success") {
      val res = eval(("resolve", "_"))
      assert(res.isSuccess == false)
      assert(res.err.contains(
        """Package declaration "package wrong" in build.sc does not match folder structure. Expected: <none>"""
      ))
      assert(res.err.contains(
        """Package declaration "package " in sub/package.sc does not match folder structure. Expected: "package sub""""
      ))
      assert(res.err.contains(
        """Package declaration "package `sub-2`" in sub-2/inner/package.sc does not match folder structure. Expected: "package `sub-2`.inner""""
      ))
    }
  }
}
