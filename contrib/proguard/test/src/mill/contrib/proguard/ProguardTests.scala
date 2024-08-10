package mill.contrib.proguard

import mill._
import mill.define.{Discover, Target}
import mill.util.Util.millProjectModule
import mill.scalalib.ScalaModule
import mill.util.TestEvaluator
import mill.util.TestUtil
import os.Path
import utest._
import utest.framework.TestPath

object ProguardTests extends TestSuite {

  object proguard extends TestUtil.BaseModule with ScalaModule with Proguard {
    // override build root to test custom builds/modules
//    override def millSourcePath: Path = TestUtil.getSrcPathStatic()
    override def millSourcePath: os.Path =
      TestUtil.getSrcPathBase() / millOuterCtx.enclosing.split('.')

    override def scalaVersion: T[String] = T(sys.props.getOrElse("MILL_SCALA_2_13_VERSION", ???))

    def proguardContribClasspath = Task {
      millProjectModule("mill-contrib-proguard", repositoriesTask())
    }

    override def runClasspath: Task[Seq[PathRef]] =
      Task { super.runClasspath() ++ proguardContribClasspath() }


    val millDiscover = Discover[this.type]
  }

  val testModuleSourcesPath: Path =
    os.pwd / "contrib" / "proguard" / "test" / "resources" / "proguard"

  def workspaceTest[T](m: TestUtil.BaseModule)(t: TestEvaluator => T)(
      implicit tp: TestPath
  ): T = {
    val eval = new TestEvaluator(m, debugEnabled = true)
    os.remove.all(m.millSourcePath)
    os.remove.all(eval.outPath)
    os.makeDir.all(m.millSourcePath / os.up)
    os.copy(testModuleSourcesPath, m.millSourcePath)
    t(eval)
  }

  def tests: Tests = Tests {
    test("Proguard module") {
      test("should download proguard jars") - workspaceTest(proguard) { eval =>
        val Right((agg, _)) = eval.apply(proguard.proguardClasspath)
        assert(
          agg.iterator.toSeq.nonEmpty,
          agg.iterator.toSeq.head.path.toString().contains("proguard-base")
        )
      }

      test("should create a proguarded jar") - workspaceTest(proguard) { eval =>
        val Right((path, _)) = eval.apply(proguard.proguard)
        assert(os.exists(path.path))
      }
    }
  }
}
