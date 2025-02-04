package mill.testkit

import mill.*
import mill.define.Discover
import utest.*

object UnitTesterTests extends TestSuite {

  val resourcePath = os.Path(sys.env("MILL_TEST_RESOURCE_DIR")) / "unit-test-example-project"
  def tests: Tests = Tests {
    test("simple") {
      object build extends TestBaseModule {
        def testTask = Task { "test" }

        lazy val millDiscover: Discover = Discover[this.type]
      }

      UnitTester(build, resourcePath).scoped { eval =>
        val Right(result) = eval(build.testTask)
        assert(result.value == "test")
      }
    }

    test("sources") {
      object build extends TestBaseModule {
        def testSource = Task.Source(millSourcePath / "source-file.txt")
        def testTask = Task { os.read(testSource().path).toUpperCase() }

        lazy val millDiscover: Discover = Discover[this.type]
      }

      UnitTester(build, resourcePath).scoped { eval =>
        val Right(result) = eval(build.testTask)
        assert(result.value == "HELLO WORLD SOURCE FILE")
      }
    }
  }
}
