package mill.eval

import mill.util.{TestEvaluator, TestUtil}
import mill.{Task, T}
import mill.define.Discover

import utest._

object ModuleTests extends TestSuite {
  object ExternalModule extends mill.define.ExternalModule {
    def x = Task { 13 }
    object inner extends mill.Module {
      def y = Task { 17 }
    }
    lazy val millDiscover = Discover[this.type]
  }
  object Build extends TestUtil.BaseModule {
    def z = Task { ExternalModule.x() + ExternalModule.inner.y() }
  }
  val tests = Tests {
    "externalModuleTargetsAreNamespacedByModulePackagePath" - {
      val check = new TestEvaluator(Build)
      os.remove.all(check.outPath)
      val zresult = check.apply(Build.z)
      assert(
        zresult == Right((30, 1)),
        os.read(check.evaluator.outPath / "z.json").contains("30"),
        os.read(
          check.outPath / "mill" / "eval" / "ModuleTests" / "ExternalModule" / "x.json"
        ).contains("13"),
        os.read(
          check.outPath / "mill" / "eval" / "ModuleTests" / "ExternalModule" / "inner" / "y.json"
        ).contains("17")
      )
    }
    "externalModuleMustBeGlobalStatic" - {

      object Build extends mill.define.ExternalModule {

        def z = Task { ExternalModule.x() + ExternalModule.inner.y() }
        lazy val millDiscover = Discover[this.type]
      }

      intercept[java.lang.AssertionError] { Build }
    }
  }
}
