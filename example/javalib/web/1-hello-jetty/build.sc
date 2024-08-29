import mill._, javalib._

object build extends RootModule with JavaModule {
  def ivyDeps = Agg(
    ivy"org.eclipse.jetty:jetty-server:9.4.43.v20210629",
    ivy"javax.servlet:javax.servlet-api:4.0.1"
  )

  object test extends JavaTests with TestModule.Junit4
}

// This example demonstrates how to set up a simple Jetty webserver,
// able to handle a single HTTP request at `/` and reply with a single response.


/** Usage

> mill test
...HelloJettyTest.testHelloJetty finished...

> mill runBackground; sleep 2 # give time for server to start

> curl http://localhost:8085
...<h1>Hello, World!</h1>...

> mill clean runBackground

*/
