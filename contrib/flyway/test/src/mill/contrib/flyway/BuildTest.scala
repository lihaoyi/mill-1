package mill.contrib.flyway

import mill._
import mill.scalalib._
import mill.testkit.TestEvaluator
import mill.testkit.MillTestKit
import utest.{TestSuite, Tests, assert, _}

object BuildTest extends TestSuite {
  object Build extends mill.testkit.BaseModule {
    object build extends FlywayModule {

      override def resources = T.sources(os.pwd / "contrib" / "flyway" / "test" / "resources")

      def h2 = ivy"com.h2database:h2:2.1.214"

      def flywayUrl = "jdbc:h2:mem:test_db;DB_CLOSE_DELAY=-1"
      def flywayDriverDeps = Agg(h2)
    }
  }

  def tests = Tests {
    test("clean") {
      val eval = new TestEvaluator(Build)
      val Right(result) = eval(Build.build.flywayClean())
      assert(result.evalCount > 0)
    }

    test("migrate") {
      val eval = new TestEvaluator(Build)
      val Right(result) = eval(Build.build.flywayMigrate())
      assert(
        result.evalCount > 0,
        result.value.migrationsExecuted == 1
      )
      val Right(resultAgain) = eval(Build.build.flywayMigrate())
      assert(
        resultAgain.evalCount > 0,
        resultAgain.value.migrationsExecuted == 0
      )
    }

    test("info") {
      val eval = new TestEvaluator(Build)
      val Right(result) = eval(Build.build.flywayInfo())
      assert(result.evalCount > 0)
    }
  }
}
