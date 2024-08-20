package mill.scalajslib

import mill.define.Discover
import mill.scalajslib.api._
import mill.testkit.TestEvaluator
import mill.testkit.MillTestKit
import utest._

object SmallModulesForTests extends TestSuite {
  val workspacePath = MillTestKit.getOutPathStatic() / "small-modules-for"

  object SmallModulesForModule extends mill.testkit.BaseModule {

    object smallModulesForModule extends ScalaJSModule {
      override def millSourcePath = workspacePath
      override def scalaVersion = sys.props.getOrElse("TEST_SCALA_2_13_VERSION", ???)
      override def scalaJSVersion =
        sys.props.getOrElse("TEST_SCALAJS_VERSION", ???) // at least "1.10.0"
      override def moduleKind = ModuleKind.ESModule
      override def moduleSplitStyle = ModuleSplitStyle.SmallModulesFor(List("app"))
    }

    override lazy val millDiscover = Discover[this.type]
  }

  val millSourcePath = os.pwd / "scalajslib" / "test" / "resources" / "small-modules-for"

  val evaluator = TestEvaluator.static(SmallModulesForModule)

  val tests: Tests = Tests {
    prepareWorkspace()

    test("ModuleSplitStyle.SmallModulesFor") {
      println(evaluator(SmallModulesForModule.smallModulesForModule.sources))

      val Right(result) = evaluator(SmallModulesForModule.smallModulesForModule.fastLinkJS)
      val publicModules = result.value.publicModules
      test("it should have a single publicModule") {
        assert(publicModules.size == 1)
      }
      test("my.Foo should not have its own file since it is in a separate package") {
        assert(!os.exists(result.value.dest.path / "otherpackage.Foo.js"))
      }
      println(os.list(result.value.dest.path))
      val modulesLength = os.list(result.value.dest.path).length

      // this changed from 10 to 8 after Scala JS version 1.13
      assert(modulesLength == 8)
    }
  }

  def prepareWorkspace(): Unit = {
    os.remove.all(workspacePath)
    os.makeDir.all(workspacePath / os.up)
    os.copy(millSourcePath, workspacePath)
  }

}
