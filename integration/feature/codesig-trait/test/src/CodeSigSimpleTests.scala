package mill.integration

import utest._

object CodeSigSimpleTests extends IntegrationTestSuite {
  val tests = Tests {
    val wsRoot = initWorkspace()
    "test" - {
      // Check normal behavior for initial run and subsequent fully-cached run
      // with no changes
      val initial = evalStdout("myObject.foo")

      assert(
        initial.out ==
        """running foo
          |running helperFoo""".stripMargin
      )

      val cached = evalStdout("myObject.foo")
      assert(cached.out == "")

      // Make sure that when we change helperFoo, even though it's in a
      // downstream object that instantiates MyModule, it still causes
      // MyModule#foo to recompute
      mangleFile(wsRoot / "build.sc", _.replace("running helperFoo", "running helperFoo2"))
      val changed = evalStdout("myObject.foo")

      assert(
        changed.out ==
          """running foo
            |running helperFoo2""".stripMargin
      )
    }
  }
}
