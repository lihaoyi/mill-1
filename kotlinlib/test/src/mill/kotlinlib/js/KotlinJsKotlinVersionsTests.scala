package mill
package kotlinlib
package js

import mill.testkit.{TestBaseModule, UnitTester}
import mill.Cross
import mill.define.Discover
import utest.{TestSuite, Tests, test}

object KotlinJsKotlinVersionsTests extends TestSuite {

  private val kotlinLowestVersion = "1.8.20"
  private val resourcePath = os.Path(sys.env("MILL_TEST_RESOURCE_DIR")) / "kotlin-js"
  private val kotlinHighestVersion = mill.kotlinlib.Versions.kotlinVersion
  private val kotlinVersions = Seq(kotlinLowestVersion, kotlinHighestVersion)

  trait KotlinJsCrossModule extends KotlinJsModule with Cross.Module[String] {
    def kotlinVersion = crossValue
  }

  trait KotlinJsFooCrossModule extends KotlinJsCrossModule {
    override def moduleDeps = Seq(module.bar(crossValue), module.qux(crossValue))
  }

  trait KotlinJsQuxCrossModule extends KotlinJsCrossModule {
    override def libraryDeps = {
      // 0.10+ cannot be built with Kotlin 1.8 (it was built with Kotlin 1.9.10 itself). ABI incompatibility?
      val kotlinxHtmlVersion = crossValue.split("\\.").map(_.toInt) match {
        case Array(1, 8, _) => "0.9.1"
        case _ => "0.11.0"
      }
      super.libraryDeps() ++ Seq(
        ivy"org.jetbrains.kotlinx:kotlinx-html-js:$kotlinxHtmlVersion"
      )
    }
  }

  object module extends TestBaseModule {
    object foo extends Cross[KotlinJsFooCrossModule](kotlinVersions)
    object bar extends Cross[KotlinJsCrossModule](kotlinVersions)
    object qux extends Cross[KotlinJsQuxCrossModule](kotlinVersions)

    lazy val millDiscover = Discover[this.type]
  }

  private def testEval() = UnitTester(module, resourcePath)

  def tests: Tests = Tests {
    test("compile with lowest Kotlin version") {
      val eval = testEval()

      val Right(_) = eval.apply(module.foo(kotlinLowestVersion).compile): @unchecked
    }

    test("compile with highest Kotlin version") {
      val eval = testEval()

      val Right(_) = eval.apply(module.foo(kotlinHighestVersion).compile): @unchecked
    }
  }

}
