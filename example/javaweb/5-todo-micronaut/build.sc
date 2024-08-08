import mill._, javalib._

object hello extends RootModule with MicronautModule {
  def micronautVersion = "4.4.3"
  def runIvyDeps = Agg(
    ivy"ch.qos.logback:logback-classic:1.5.3",
    ivy"com.h2database:h2:2.2.224",
  )

  def ivyDeps = Agg(
    ivy"io.micronaut:micronaut-http-server-netty:$micronautVersion",
    ivy"io.micronaut.serde:micronaut-serde-jackson:2.9.0",
    ivy"io.micronaut.data:micronaut-data-jdbc:4.7.0",
    ivy"io.micronaut.sql:micronaut-jdbc-hikari:5.6.0",
    ivy"io.micronaut.validation:micronaut-validation:4.5.0",

    ivy"io.micronaut.views:micronaut-views-htmx:5.2.0",
    ivy"io.micronaut.views:micronaut-views-thymeleaf:5.2.0",

    ivy"org.webjars.npm:todomvc-common:1.0.5",
    ivy"org.webjars.npm:todomvc-app-css:2.4.1",
    ivy"org.webjars.npm:github-com-bigskysoftware-htmx:1.9.10",
  )


  object test extends MavenTests with TestModule.Junit5{
    def ivyDeps = super.ivyDeps() ++ Agg(
      ivy"com.h2database:h2:2.2.224",

      ivy"io.micronaut:micronaut-http-client:$micronautVersion",
      ivy"io.micronaut.test:micronaut-test-junit5:4.4.0",

      ivy"org.junit.jupiter:junit-jupiter-api:5.8.1",
      ivy"org.junit.jupiter:junit-jupiter-engine:5.8.1"
    )
  }
}

trait MicronautModule extends MavenModule{
  def micronautVersion: String

  def processors = task {
    defaultResolver().resolveDeps(
      Agg(
        ivy"io.micronaut.data:micronaut-data-processor:4.7.0",
        ivy"io.micronaut:micronaut-http-validation:$micronautVersion",
        ivy"io.micronaut.serde:micronaut-serde-processor:2.9.0",
        ivy"io.micronaut.validation:micronaut-validation-processor:4.5.0",
        ivy"io.micronaut:micronaut-inject-java:$micronautVersion"
      )
    )
  }

  def javacOptions = Seq(
    "-processorpath",
    processors().map(_.path).mkString(":"),
    "-parameters",
    "-Amicronaut.processing.incremental=true",
    "-Amicronaut.processing.group=example.micronaut",
    "-Amicronaut.processing.module=todo",
    "-Amicronaut.processing.annotations=example.micronaut.*",
  )
}


// This example is a more complete example using Micronaut, adapted from
// https://github.com/sdelamo/todomvc. On top of the `MicronautModule` and
// annotation processing demonstrated by the previous example, this example
// shows how a "full stack" web application using Micronaut looks like:
//
// * Thymeleaf for HTML templating
// * Webjars for Javascript and CSS
// * HTMX for interactivity
// * Database interactions using JDBC and H2
// * Controllers, Repositories, Entities, Forms
// * A more detailed test suite
//
// Again, the example `MicronautModule` is by no means complete, but it demonstrates
// how Mill can be integrated with Micronaut's annotation processors and configuration,
// and can be extended to cover additional functionality in future


/** Usage

> mill test
...example.micronaut.LearnJsonTest...
...example.micronaut.TodoTest...
...example.micronaut.TodoItemMapperTest...
...example.micronaut.TodoItemControllerTest...
...example.micronaut.HtmxWebJarsTest...

> mill runBackground

> curl http://localhost:8088
 ...<h1>todos</h1>...

> mill clean runBackground

*/
