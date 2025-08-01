package webapp

import utest.*

object WebAppTests extends TestSuite {
  def withServer[T](example: cask.main.Main)(f: String => T): T = {
    val server = io.undertow.Undertow.builder
      .addHttpListener(8185, "localhost")
      .setHandler(example.defaultHandler)
      .build
    server.start()
    val res =
      try f("http://localhost:8185")
      finally server.stop()
    res
  }

  val tests = Tests {
    test("simpleRequest") - withServer(WebApp) { host =>
      val page = requests.get(host).text()
      assert(page.contains("What needs to be done?"))
    }
  }
}
