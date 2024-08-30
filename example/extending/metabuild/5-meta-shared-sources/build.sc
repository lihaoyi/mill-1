import $meta._
import mill._, scalalib._

object `package` extends RootModule with ScalaModule {
  def scalaVersion = millbuild.ScalaVersion.myScalaVersion
  def sources = T.sources{
    super.sources() ++ Seq(PathRef(millSourcePath / "mill-build" / "src"))
  }
}

/** See Also: mill-build/build.sc */
/** See Also: mill-build/src/ScalaVersion.scala */
/** See Also: src/Foo.scala */

// This example shows another use of the Mill meta-build: because the meta-build
// is just a normal Scala module (with some special handling for the `.sc` file extension),
// your `build.sc` file can take normal Scala source files that are placed in `mill-build/src/`
// This allows us to share those sources with your application code by appropriately
// configuring your `def sources` in `build.sc` above.

/** Usage

> mill run
scalaVersion: 2.13.10

*/

// Here we only share a trivial `def myScalaVersion = "2.13.10"` definition between
// the `build.sc` and application code. But this ability to share arbitrary code between
// your application and your build opens up a lot of possibilities:
//
// * Run-time initialization logic can be placed in `mill-build/src/`, shared with the
//   `build.sc` and used to perform build-time preprocessing, reducing the work needing
//   to be done at application start and reducing startup latencies.
//
// * Build-time `build.sc` logic can be placed in `mill-build/src/` and shared with your
//   run-time application code, allowing greater flexibility in e.g. exercising the shared
//   logic in response to user input that's not available at build time.
//
// In general, the Mill meta-build with its `mill-build` folder is meant to blur the line
// between build-time logic and run-time logic. Often you want to do the same thing in both
// places: at build-time where possible to save time at runtime, and at runtime where
// necessary to make use of user input. With the Mill meta-build, you can write logic
// comprising the same source code and using the same libraries in both environments,
// giving you flexibility in where your logic ends up running