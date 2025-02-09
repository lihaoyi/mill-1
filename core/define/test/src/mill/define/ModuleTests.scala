package mill.define

import mill.define.{Discover, DynamicModule, ModuleRef, NamedTask, TaskModule}
import mill.testkit.TestBaseModule
import mill.util.TestGraphs
import mill.util.TestGraphs.*
import mill.{Cross, Module, Task}
import utest.*

object ModuleTests extends TestSuite {

  val tests = Tests {

    object graphs extends TestGraphs()
    import graphs._
    import TestGraphs._

    test("test"){
      singleton.toString ==> ""
      singleton.single.toString ==> "single"
    }
    test("cross"){
      pprint.log(nestedCrosses.cross("210").millModuleSegments)
      pprint.log(nestedCrosses.cross("210").millOuterCtx.segments)
      pprint.log(nestedCrosses.cross("210").cross2.millModuleSegments)
      pprint.log(nestedCrosses.cross("210").cross2.millOuterCtx.segments)
      nestedCrosses.toString ==> ""
      nestedCrosses.cross.toString ==> "cross"
      nestedCrosses.cross("210").toString ==> "cross[210]"
      nestedCrosses.cross("210").cross2.toString ==> "cross[210].cross2"
      nestedCrosses.cross("210").cross2("js").toString ==> "cross[210].cross2[js]"
    }
  }
}
