package mill.scalalib

import utest._

class CustomFramework extends utest.runner.Framework {
  override def setup(): Unit = {
    println("CUSTOM_FRAMEWORK_SETUP")
  }

  override def teardown(): Unit = {
    println("CUSTOM_FRAMEWORK_TEARDOWN")
  }
}
