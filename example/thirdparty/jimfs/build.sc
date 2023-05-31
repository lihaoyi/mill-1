import mill._, scalalib._, publish._

object jimfs extends PublishModule with MavenModule{
  def publishVersion = "1.3.3.7"

  def pomSettings = PomSettings(
    description = artifactName(),
    organization = "com.google",
    url = "https://github.com/google/jimfs",
    licenses = Seq(License.MIT),
    versionControl = VersionControl.github(owner = "google", repo = "jimfs"),
    developers = Nil
  )

  def ivyDeps = Agg(
    ivy"com.google.guava:guava:31.1-android",
  )

  def compileIvyDeps = Agg(
    ivy"com.google.auto.service:auto-service-annotations:1.0.1",
    ivy"com.google.code.findbugs:jsr305:3.0.2",
    ivy"org.checkerframework:checker-compat-qual:2.5.5",
    ivy"com.ibm.icu:icu4j:73.1",
  )

  object test extends MavenModuleTests{
    def ivyDeps = Agg(
      ivy"junit:junit:4.13.2",
      ivy"com.google.guava:guava-testlib:31.1-android",
      ivy"com.google.truth:truth:1.1.3",
      ivy"com.novocode:junit-interface:0.11",
      ivy"com.ibm.icu:icu4j:73.1",
    )

    def testFramework = "com.novocode.junit.JUnitFramework"
  }
}

// JimFS is a small Java library

/** Usage

> ./mill jimfs.compile

> ./mill jimfs.test

*/