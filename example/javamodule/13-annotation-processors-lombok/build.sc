import mill._, javalib._


object foo extends JavaModule {
  def compileIvyDeps = Agg(ivy"org.projectlombok:lombok:1.18.34")

  object test extends JavaTests with TestModule.Junit4
}

// This is an example of how to use Mill to build Java projects using Java annotations and
// annotation processors. In this case, we use the annotations provided by
// https://projectlombok.org[Project Lombok] to automatically generate getters and setters
// from class private fields

/** Usage

> ./mill foo.test
Test foo.HelloWorldTest.testSimple started
Test foo.HelloWorldTest.testSimple finished...
...

*/

// The Java compiler automatically discovers annotation processors based on the
// classes available during compilation, e.g. on `compileIvyDeps` or `ivyDeps`,
// which is what takes place in the example above.
//
// In some cases, you may need
// to pass in the annotation processors manually, e.g. if you need annotation
// processors that are not on the compilation classpath, or you need finer control
// over exactly which annotation processors are active. To do this, you can define
// a module to contain the exact annotation processors you want, and pass
// in `-processorpath` to `javacOptions` explicitly:

object processors extends JavaModule{
  def ivyDeps = Agg(ivy"org.projectlombok:lombok:1.18.34")
}

object bar extends JavaModule {
  def compileIvyDeps = Agg(ivy"org.projectlombok:lombok:1.18.34")

  def javacOptions = Seq(
    "-processorpath",
    processors.runClasspath().map(_.path).mkString(":"),
  )

  object test extends JavaTests with TestModule.Junit4
}

/** Usage

 > ./mill bar.test
Test bar.HelloWorldTest.testSimple started
Test bar.HelloWorldTest.testSimple finished...
...

*/