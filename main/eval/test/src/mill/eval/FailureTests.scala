package mill.eval

import mill.{T, Task}
import mill.util.{TestEvaluator, TestUtil}
import mill.api.Result.OuterStack
import mill.define.Discover
import utest._

object FailureTests extends TestSuite {

  val tests = Tests {
    val graphs = new mill.util.TestGraphs()
    import graphs._

    "evaluateSingle" - {
      val check = new TestEvaluator(singleton)
      check.fail(
        target = singleton.single,
        expectedFailCount = 0,
        expectedRawValues = Seq(mill.api.Result.Success(0))
      )

      singleton.single.failure = Some("lols")

      check.fail(
        target = singleton.single,
        expectedFailCount = 1,
        expectedRawValues = Seq(mill.api.Result.Failure("lols"))
      )

      singleton.single.failure = None

      check.fail(
        target = singleton.single,
        expectedFailCount = 0,
        expectedRawValues = Seq(mill.api.Result.Success(0))
      )

      val ex = new IndexOutOfBoundsException()
      singleton.single.exception = Some(ex)

      check.fail(
        target = singleton.single,
        expectedFailCount = 1,
        expectedRawValues = Seq(mill.api.Result.Exception(ex, new OuterStack(Nil)))
      )
    }
    "evaluatePair" - {
      val check = new TestEvaluator(pair)
      check.fail(
        pair.down,
        expectedFailCount = 0,
        expectedRawValues = Seq(mill.api.Result.Success(0))
      )

      // inject some fake error
      pair.up.failure = Some("lols")

      check.fail(
        pair.down,
        expectedFailCount = 1,
        expectedRawValues = Seq(mill.api.Result.Skipped)
      )

      pair.up.failure = None

      check.fail(
        pair.down,
        expectedFailCount = 0,
        expectedRawValues = Seq(mill.api.Result.Success(0))
      )

      pair.up.exception = Some(new IndexOutOfBoundsException())

      check.fail(
        pair.down,
        expectedFailCount = 1,
        expectedRawValues = Seq(mill.api.Result.Skipped)
      )
    }

    "evaluatePair (failFast=true)" - {
      val check = new TestEvaluator(pair, failFast = true)
      check.fail(
        pair.down,
        expectedFailCount = 0,
        expectedRawValues = Seq(mill.api.Result.Success(0))
      )

      pair.up.failure = Some("lols")

      check.fail(
        pair.down,
        expectedFailCount = 1,
        expectedRawValues = Seq(mill.api.Result.Aborted)
      )

      pair.up.failure = None

      check.fail(
        pair.down,
        expectedFailCount = 0,
        expectedRawValues = Seq(mill.api.Result.Success(0))
      )

      pair.up.exception = Some(new IndexOutOfBoundsException())

      check.fail(
        pair.down,
        expectedFailCount = 1,
        expectedRawValues = Seq(mill.api.Result.Aborted)
      )
    }

    "evaluateBacktickIdentifiers" - {
      val check = new TestEvaluator(bactickIdentifiers)
      import bactickIdentifiers._
      check.fail(
        `a-down-target`,
        expectedFailCount = 0,
        expectedRawValues = Seq(mill.api.Result.Success(0))
      )

      `up-target`.failure = Some("lols")

      check.fail(
        `a-down-target`,
        expectedFailCount = 1,
        expectedRawValues = Seq(mill.api.Result.Skipped)
      )

      `up-target`.failure = None

      check.fail(
        `a-down-target`,
        expectedFailCount = 0,
        expectedRawValues = Seq(mill.api.Result.Success(0))
      )

      `up-target`.exception = Some(new IndexOutOfBoundsException())

      check.fail(
        `a-down-target`,
        expectedFailCount = 1,
        expectedRawValues = Seq(mill.api.Result.Skipped)
      )
    }

    "evaluateBacktickIdentifiers (failFast=true)" - {
      val check = new TestEvaluator(bactickIdentifiers, failFast = true)
      import bactickIdentifiers._
      check.fail(
        `a-down-target`,
        expectedFailCount = 0,
        expectedRawValues = Seq(mill.api.Result.Success(0))
      )

      `up-target`.failure = Some("lols")

      check.fail(
        `a-down-target`,
        expectedFailCount = 1,
        expectedRawValues = Seq(mill.api.Result.Aborted)
      )

      `up-target`.failure = None

      check.fail(
        `a-down-target`,
        expectedFailCount = 0,
        expectedRawValues = Seq(mill.api.Result.Success(0))
      )

      `up-target`.exception = Some(new IndexOutOfBoundsException())

      check.fail(
        `a-down-target`,
        expectedFailCount = 1,
        expectedRawValues = Seq(mill.api.Result.Aborted)
      )
    }

    "multipleUsesOfDest" - {
      object build extends TestUtil.BaseModule {
        // Using `Task.ctx(  ).dest` twice in a single task is ok
        def left = Task { +Task.dest.toString.length + Task.dest.toString.length }

        // Using `Task.ctx(  ).dest` once in two different tasks is ok
        val task = Task.anon { Task.dest.toString.length }
        def right = Task { task() + left() + Task.dest.toString().length }

        val millDiscover = Discover[this.type]
      }

      val check = new TestEvaluator(build)
      assert(check(build.left).isRight)
      assert(check(build.right).isRight)
      // assert(e.getMessage.contains("`dest` can only be used in one place"))
    }
  }
}
