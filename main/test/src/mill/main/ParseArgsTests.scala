package mill.main

import mill.define.{Segment, Segments}
import mill.define.Segment.{Cross, Label}
import mill.main.ParseArgs.TargetSeparator
import utest._

object ParseArgsTests extends TestSuite {

  val tests = Tests {
    "extractSelsAndArgs" - {
      def check(
          input: Seq[String],
          expectedSelectors: Seq[String],
          expectedArgs: Seq[String],
      ) = {
        val (selectors, args) = ParseArgs.extractSelsAndArgs(input)

        assert(
          selectors == expectedSelectors,
          args == expectedArgs
        )
      }

      "empty" - check(
        input = Seq.empty,
        expectedSelectors = Seq.empty,
        expectedArgs = Seq.empty,
      )
      "singleSelector" - check(
        input = Seq("core.compile"),
        expectedSelectors = Seq("core.compile"),
        expectedArgs = Seq.empty,
      )
      "singleSelectorWithArgs" - check(
        input = Seq("application.run", "hello", "world"),
        expectedSelectors = Seq("application.run"),
        expectedArgs = Seq("hello", "world"),
      )
      "singleSelectorWithAllInArgs" - check(
        input = Seq("application.run", "hello", "world", "--all"),
        expectedSelectors = Seq("application.run"),
        expectedArgs = Seq("hello", "world", "--all"),
      )
    }

    "apply(multiselect)" - {
      def check(
          input: Seq[String],
          expectedSelectors: List[(Option[List[Segment]], List[Segment])],
          expectedArgs: Seq[String],
      ) = {
        val Right((selectors0, args) :: _) =
          ParseArgs(input)

        val selectors = selectors0.map {
          case (Some(v1), v2) => (Some(v1.value), v2.value)
          case (None, v2) => (None, v2.value)
        }
        assert(
          selectors == expectedSelectors,
          args == expectedArgs
        )
      }

      "rejectEmpty" - {
        val parsed = ParseArgs(Seq.empty)
        assert(
          parsed == Left("Selector cannot be empty")
        )
      }
      "singleSelector" - check(
        input = Seq("core.compile"),
        expectedSelectors = List(
          None -> List(Label("core"), Label("compile"))
        ),
        expectedArgs = Seq.empty,
      )
      "externalSelector" - check(
        input = Seq("foo.bar/core.compile"),
        expectedSelectors = List(
          Some(List(Label("foo"), Label("bar"))) -> List(Label("core"), Label("compile"))
        ),
        expectedArgs = Seq.empty,
      )
      "singleSelectorWithArgs" - check(
        input = Seq("application.run", "hello", "world"),
        expectedSelectors = List(
          None -> List(Label("application"), Label("run"))
        ),
        expectedArgs = Seq("hello", "world"),
      )
      "singleSelectorWithCross" - check(
        input = Seq("bridges[2.12.4,jvm].compile"),
        expectedSelectors = List(
          None -> List(Label("bridges"), Cross(Seq("2.12.4", "jvm")), Label("compile"))
        ),
        expectedArgs = Seq.empty,
      )
    }

    test("apply(SelectMode.Separated)") {
      def parsed(args: String*) = ParseArgs(args)
      test("rejectEmpty") {
        assert(parsed("") == Left("Selector cannot be empty"))
        assert(parsed() == Left("Selector cannot be empty"))
      }
      def check(
          input: Seq[String],
          expectedSelectorArgPairs: Seq[(Seq[(Option[Seq[Segment]], Seq[Segment])], Seq[String])]
      ) = {
        val Right(parsed) = ParseArgs(input)
        val actual = parsed.map {
          case (selectors0, args) =>
            val selectors = selectors0.map {
              case (Some(v1), v2) => (Some(v1.value), v2.value)
              case (None, v2) => (None, v2.value)
            }
            (selectors, args)
        }
        assert(
          actual == expectedSelectorArgPairs
        )
      }

      test("singleTopLevelTarget") {
        check(
          Seq("compile"),
          Seq(
            Seq(
              None -> Seq(Label("compile"))
            ) -> Seq.empty
          )
        )
      }
      test("singleTarget") {
        check(
          Seq("core.compile"),
          Seq(
            Seq(
              None -> Seq(Label("core"), Label("compile"))
            ) -> Seq.empty
          )
        )
      }
      test("multiTargets") {
        check(
          Seq("core.compile", ParseArgs.TargetSeparator, "app.compile"),
          Seq(
            Seq(
              None -> Seq(Label("core"), Label("compile"))
            ) -> Seq.empty,
            Seq(
              None -> Seq(Label("app"), Label("compile"))
            ) -> Seq.empty
          )
        )
      }
      test("multiTargetsSupportMaskingSeparator") {
        check(
          Seq(
            "core.run",
            """\""" + ParseArgs.TargetSeparator,
            "arg2",
            "+",
            "run",
            """\\""" + ParseArgs.TargetSeparator,
            """\\\""" + ParseArgs.TargetSeparator,
            """x\\""" + ParseArgs.TargetSeparator
          ),
          Seq(
            Seq(
              None -> Seq(Label("core"), Label("run"))
            ) -> Seq(ParseArgs.TargetSeparator, "arg2"),
            Seq(
              None -> Seq(Label("run"))
            ) -> Seq(
              """\""" + TargetSeparator,
              """\\""" + TargetSeparator,
              """x\\""" + TargetSeparator
            )
          )
        )
      }
      test("singleTargetWithArgs") {
        check(
          Seq("core.run", "arg1", "arg2"),
          Seq(
            Seq(
              None -> List(Label("core"), Label("run"))
            ) -> Seq("arg1", "arg2")
          )
        )
      }
      test("multiTargetsWithArgs") {
        check(
          Seq("core.run", "arg1", "arg2", ParseArgs.TargetSeparator, "core.runMain", "my.main"),
          Seq(
            Seq(
              None -> Seq(Label("core"), Label("run"))
            ) -> Seq("arg1", "arg2"),
            Seq(
              None -> Seq(Label("core"), Label("runMain"))
            ) -> Seq("my.main")
          )
        )
      }
      test("multiTargetsWithArgsAndBrace") {
        check(
          Seq(
            "{core,app,test._}.run",
            "arg1",
            "arg2",
            ParseArgs.TargetSeparator,
            "core.runMain",
            "my.main"
          ),
          Seq(
            Seq(
              None -> Seq(Label("core"), Label("run")),
              None -> Seq(Label("app"), Label("run")),
              None -> Seq(Label("test"), Label("_"), Label("run"))
            ) -> Seq("arg1", "arg2"),
            Seq(
              None -> Seq(Label("core"), Label("runMain"))
            ) -> Seq("my.main")
          )
        )
      }
    }

  }
}
