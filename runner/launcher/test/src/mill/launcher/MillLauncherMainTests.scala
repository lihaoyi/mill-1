package mill.launcher

import utest.*

object MillLauncherMainTests extends TestSuite {
  val tests = Tests {
    test("launcherInitializationBoundary") {
      val existingKey = "mill.test.issue6888.existing"
      val addedKey = "mill.test.issue6888.added"
      val originalExisting = Option(System.getProperty(existingKey))
      val originalAdded = Option(System.getProperty(addedKey))
      val properties = Map(existingKey -> "overridden", addedKey -> "added")

      try {
        System.setProperty(existingKey, "original")
        System.clearProperty(addedKey)

        MillLauncherMain.withSystemProperties(properties) {
          assert(
            System.getProperty(existingKey) == "overridden",
            System.getProperty(addedKey) == "added"
          )
        }

        assert(System.getProperty(existingKey) == "original")
        assert(System.getProperty(addedKey) == null)

        assertThrows[RuntimeException] {
          MillLauncherMain.withSystemProperties(properties) {
            throw new RuntimeException("stop initialization")
          }
        }

        assert(System.getProperty(existingKey) == "original")
        assert(System.getProperty(addedKey) == null)
      } finally {
        originalExisting match {
          case Some(value) => System.setProperty(existingKey, value)
          case None => System.clearProperty(existingKey)
        }
        originalAdded match {
          case Some(value) => System.setProperty(addedKey, value)
          case None => System.clearProperty(addedKey)
        }
      }
    }
  }
}
