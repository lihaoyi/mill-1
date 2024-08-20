package mill.eval

import mill.testkit.TestEvaluator
import mill.testkit.MillTestKit
import mill.T
import mill.define.Discover

import utest._

object ModuleTests extends TestSuite {
  object ExternalModule extends mill.define.ExternalModule {
    def x = T { 13 }
    object inner extends mill.Module {
      def y = T { 17 }
    }
    lazy val millDiscover = Discover[this.type]
  }
  object Build extends mill.testkit.BaseModule {
    def z = T { ExternalModule.x() + ExternalModule.inner.y() }
  }
  val tests = Tests {
    test("externalModuleTargetsAreNamespacedByModulePackagePath") {
      val check = new TestEvaluator(Build)
      os.remove.all(check.outPath)
      val zresult = check.apply(Build.z)
      assert(
        zresult == Right(TestEvaluator.Result(30, 1)),
        os.read(check.evaluator.outPath / "z.json").contains("30"),
        os.read(
          check.outPath / "mill" / "eval" / "ModuleTests" / "ExternalModule" / "x.json"
        ).contains("13"),
        os.read(
          check.outPath / "mill" / "eval" / "ModuleTests" / "ExternalModule" / "inner" / "y.json"
        ).contains("17")
      )
    }
    test("externalModuleMustBeGlobalStatic") {

      object Build extends mill.define.ExternalModule {

        def z = T { ExternalModule.x() + ExternalModule.inner.y() }
        lazy val millDiscover = Discover[this.type]
      }

      intercept[java.lang.AssertionError] { Build }
    }
  }
}
