import mill._, javalib._, publish._

object hello extends RootModule with JavaModule {
  def ivyDeps = Agg(
    ivy"org.springframework.boot:spring-boot-starter-web:2.5.6",
    ivy"org.springframework.boot:spring-boot-starter-actuator:2.5.6"
  )

  object test extends JavaModuleTests with TestModule.Junit5 {
    def ivyDeps = super.ivyDeps() ++ Agg(
      ivy"org.springframework.boot:spring-boot-starter-test:2.5.6"
    )
  }
}

// This example demonstrates how to set up a simple Spring Boot webserver,
// able to handle a single HTTP request at `/` and reply with a single response.


/** Usage

> mill test
...com.example.HelloSpringBootTest#shouldReturnDefaultMessage() finished...

> mill runBackground

> curl http://localhost:808=6
...<h1>Hello, World!</h1>...

> mill clean runBackground

*/
