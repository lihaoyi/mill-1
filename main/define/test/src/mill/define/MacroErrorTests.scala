package mill.define

import utest._
import mill.{T, task, Module}
import mill.util.TestUtil
object MacroErrorTests extends TestSuite {

  val tests = Tests {

    "errors" - {
      val expectedMsg =
        "T{} members must be defs defined in a Cacher class/trait/object body"

      val err = compileError("object Foo extends TestUtil.BaseModule{ val x = T{1} }")
      assert(err.msg == expectedMsg)
    }

    "badParameterSets" - {
      "command" - {
        val e = compileError("""
          object foo extends mill.util.TestUtil.BaseModule{
            def w = task.command{1}
          }
          mill.define.Discover[foo.type]
        """)
        assert(
          e.msg.contains("`task.command` definitions must have 1 parameter list"),
          e.pos.contains("def w = ")
        )
      }

      "target" - {
        val e = compileError("""
          object foo extends mill.util.TestUtil.BaseModule{
            def x() = T{1}
          }
          mill.define.Discover[foo.type]
        """)
        assert(
          e.msg.contains("Target definitions must have 0 parameter lists"),
          e.pos.contains("def x() = ")
        )
      }
      "input" - {
        val e = compileError("""
          object foo extends mill.util.TestUtil.BaseModule{
            def y() = task.input{1}
          }
          mill.define.Discover[foo.type]
        """)
        assert(
          e.msg.contains("Target definitions must have 0 parameter lists"),
          e.pos.contains("def y() = ")
        )
      }
      "sources" - {
        val e = compileError("""
          object foo extends mill.util.TestUtil.BaseModule{
            def z() = task.sources{os.pwd}
          }
          mill.define.Discover[foo.type]
        """)
        assert(
          e.msg.contains("Target definitions must have 0 parameter lists"),
          e.pos.contains("def z() = ")
        )
      }
      "persistent" - {
        val e = compileError("""
          object foo extends mill.util.TestUtil.BaseModule{
            def a() = task.persistent{1}
          }
          mill.define.Discover[foo.type]
        """)
        assert(
          e.msg.contains("Target definitions must have 0 parameter lists"),
          e.pos.contains("def a() = ")
        )
      }
    }
    "badTmacro" - {
      // Make sure we can reference values from outside the T{...} block as part
      // of our `Target#apply()` calls, but we cannot reference any values that
      // come from inside the T{...} block
      "pos" - {
        val e = compileError("""
          val a = T{ 1 }
          val arr = Array(a)
          val b = {
            val c = 0
            T{
              arr(c)()
            }
          }
        """)
        assert(e.msg.contains(
          "Modules, Targets and Commands can only be defined within a mill Module"
        ))
      }
      "neg" - {

        val expectedMsg =
          "Target#apply() call cannot use `value n` defined within the T{...} block"
        val err = compileError("""new Module{
          def a = T{ 1 }
          val arr = Array(a)
          def b = {
            T{
              val n = 0
              arr(n)()
            }
          }
        }""")
        assert(err.msg == expectedMsg)
      }
      "neg2" - {

        val expectedMsg =
          "Target#apply() call cannot use `value x` defined within the T{...} block"
        val err = compileError("""new Module{
          def a = T{ 1 }
          val arr = Array(a)
          def b = {
            T{
              arr.map{x => x()}
            }
          }
        }""")
        assert(err.msg == expectedMsg)
      }
      "neg3" - {
        val borkedCachedDiamond1 = utest.compileError("""
          object borkedCachedDiamond1 {
            def up = T{ TestUtil.test() }
            def left = T{ TestUtil.test(up) }
            def right = T{ TestUtil.test(up) }
            def down = T{ TestUtil.test(left, right) }
          }
        """)
        assert(borkedCachedDiamond1.msg.contains(
          "Modules, Targets and Commands can only be defined within a mill Module"
        ))
      }
    }

    "badCrossKeys" - {
      val error = utest.compileError(
        """
        object foo extends mill.util.TestUtil.BaseModule{
          object cross extends Cross[MyCrossModule](Seq(1, 2, 3))
          trait MyCrossModule extends Cross.Module[String]
        }
      """
      )
      assert(error.msg.contains("type mismatch;"))
      assert(error.msg.contains("found   : Int"))
      assert(error.msg.contains("required: String"))
    }

    "invalidCrossType" - {
      val error = utest.compileError(
        """
        object foo extends mill.util.TestUtil.BaseModule{
          object cross extends Cross[MyCrossModule](null.asInstanceOf[sun.misc.Unsafe])
          trait MyCrossModule extends Cross.Module[sun.misc.Unsafe]
        }
      """
      )
      assert(error.msg.contains(
        "could not find implicit value for evidence parameter of type mill.define.Cross.ToSegments[sun.misc.Unsafe]"
      ))
    }
  }
}
