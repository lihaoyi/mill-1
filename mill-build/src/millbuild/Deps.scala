package millbuild

import mill.scalalib.*
import mill.javalib.api.*

object Deps {

  // The Scala version to use
  // When updating, run "Publish Bridges" Github Actions for the new version
  // and then add to it `bridgeScalaVersions`
  val scalaVersion = "3.7.1"
  val scala2Version = "2.13.15"
  // The Scala 2.12.x version to use for some workers
  val workerScalaVersion212 = "2.12.20"
  val sbtScalaVersion212 = workerScalaVersion212

  val testScala213Version = "2.13.16"
  // Scala Native 4.2 will not get releases for new Scala version
  val testScala213VersionForScalaNative42 = "2.13.16"
  val testScala212Version = "2.12.6"
  val testScala32Version = "3.2.0"
  val testScala33Version = "3.3.1"

  object Scalajs_1 {
    val scalaJsVersion = "1.19.0"
    val scalajsEnvJsdomNodejs =
      mvn"org.scala-js::scalajs-env-jsdom-nodejs:1.1.0".withDottyCompat(scalaVersion)
    val scalajsEnvExoegoJsdomNodejs =
      mvn"net.exoego::scalajs-env-jsdom-nodejs:2.1.0".withDottyCompat(scalaVersion)
    val scalajsEnvNodejs = mvn"org.scala-js::scalajs-env-nodejs:1.4.0".withDottyCompat(scalaVersion)
    val scalajsEnvPhantomjs =
      mvn"org.scala-js::scalajs-env-phantomjs:1.0.0".withDottyCompat(scalaVersion)
    val scalajsEnvSelenium =
      mvn"org.scala-js::scalajs-env-selenium:1.1.1".withDottyCompat(scalaVersion)
    val scalajsSbtTestAdapter =
      mvn"org.scala-js::scalajs-sbt-test-adapter:${scalaJsVersion}".withDottyCompat(scalaVersion)
    val scalajsLinker =
      mvn"org.scala-js::scalajs-linker:${scalaJsVersion}".withDottyCompat(scalaVersion)
    val scalajsImportMap =
      mvn"com.armanbilge::scalajs-importmap:0.1.1".withDottyCompat(scalaVersion)
  }

  object Scalanative_0_5 {
    val scalanativeVersion = "0.5.8"
    val scalanativeTools = mvn"org.scala-native::tools:${scalanativeVersion}"
    val scalanativeUtil = mvn"org.scala-native::util:${scalanativeVersion}"
    val scalanativeNir = mvn"org.scala-native::nir:${scalanativeVersion}"
    val scalanativeTestRunner = mvn"org.scala-native::test-runner:${scalanativeVersion}"
  }

  trait Play {
    def playVersion: String
    def playBinVersion: String = playVersion.split("[.]").take(2).mkString(".")
    def routesCompiler = playBinVersion match {
      case "2.6" | "2.7" | "2.8" => mvn"com.typesafe.play::routes-compiler::$playVersion"
      case "2.9" => mvn"com.typesafe.play::play-routes-compiler::$playVersion"
      case _ => mvn"org.playframework::play-routes-compiler::$playVersion"
    }
    def scalaVersion: String = Deps.scalaVersion
  }
  object Play_2_6 extends Play {
    def playVersion = "2.6.25"
    override def scalaVersion: String = Deps.workerScalaVersion212
  }
  object Play_2_7 extends Play {
    val playVersion = "2.7.9"
    override def scalaVersion: String = Deps.scala2Version
  }
  object Play_2_8 extends Play {
    val playVersion = "2.8.22"
    override def scalaVersion: String = Deps.scala2Version
  }
  object Play_2_9 extends Play {
    val playVersion = "2.9.6"
  }
  object Play_3_0 extends Play {
    val playVersion = "3.0.6"
  }
  val play =
    Seq(Play_3_0, Play_2_9, Play_2_8, Play_2_7, Play_2_6).map(p => (p.playBinVersion, p)).toMap

  val acyclic = mvn"com.lihaoyi:::acyclic:0.3.18"
  val ammoniteVersion = "3.0.2"
  val asmTree = mvn"org.ow2.asm:asm-tree:9.8"
  val bloopConfig = mvn"ch.epfl.scala::bloop-config:1.5.5".withDottyCompat(scalaVersion)

  val classgraph = mvn"io.github.classgraph:classgraph:4.8.180"
  val coursierVersion = "2.1.25-M16"
  val coursier = mvn"io.get-coursier::coursier:$coursierVersion".withDottyCompat(scalaVersion)
  val coursierArchiveCache =
    mvn"io.get-coursier::coursier-archive-cache:$coursierVersion".withDottyCompat(scalaVersion)
  val coursierCore =
    mvn"io.get-coursier::coursier-core:$coursierVersion".withDottyCompat(scalaVersion)
  val coursierCache =
    mvn"io.get-coursier::coursier-cache:$coursierVersion".withDottyCompat(scalaVersion)
  val coursierPaths =
    mvn"io.get-coursier:coursier-paths:$coursierVersion".withDottyCompat(scalaVersion)
  val coursierUtil =
    mvn"io.get-coursier::coursier-util:$coursierVersion".withDottyCompat(scalaVersion)
  val coursierVersions = mvn"io.get-coursier::versions:0.5.1".withDottyCompat(scalaVersion)
  val coursierInterface = mvn"io.get-coursier:interface:1.0.29-M1"
  val coursierJvm =
    mvn"io.get-coursier::coursier-jvm:$coursierVersion".withDottyCompat(scalaVersion)

  val cask = mvn"com.lihaoyi::cask:0.9.4"
  val castor = mvn"com.lihaoyi::castor:0.3.0"
  val fastparse = mvn"com.lihaoyi::fastparse:3.1.1"
  val flywayCore = mvn"org.flywaydb:flyway-core:8.5.13"
  val graphvizJava = Seq(
    mvn"guru.nidi:graphviz-java-min-deps:0.18.1",
    mvn"org.webjars.npm:viz.js-graphviz-java:2.1.3",
    mvn"org.apache.xmlgraphics:batik-rasterizer:1.18"
  )
  val graphvizWithExcludes = mvn"guru.nidi:graphviz-java-min-deps:0.18.1"
    // We only need the in-memory library for some stuff, and don't
    // need the heavyweight v8 binary that comes bundled with it
    .exclude(
      "guru.nidi.com.eclipsesource.j2v8" -> "j2v8_macosx_x86_64",
      "guru.nidi.com.eclipsesource.j2v8" -> "j2v8_linux_x86_64"
    )

  val jgraphtCore = mvn"org.jgrapht:jgrapht-core:1.4.0" // 1.5.0+ dont support JDK8
  val javet = Seq(
    mvn"com.caoccao.javet:javet:4.0.0",
    mvn"com.caoccao.javet:javet-linux-arm64:4.0.0",
    mvn"com.caoccao.javet:javet-macos:4.0.0"
  )

  val jline = mvn"org.jline:jline:3.28.0"
  val jnaVersion = "5.16.0"

  val jna = mvn"net.java.dev.jna:jna:${jnaVersion}"
  val jnaPlatform = mvn"net.java.dev.jna:jna-platform:${jnaVersion}"

  val junitInterface = mvn"com.github.sbt:junit-interface:0.13.3"
  val commonsIo = mvn"commons-io:commons-io:2.18.0"
  val log4j2Core = mvn"org.apache.logging.log4j:log4j-core:2.24.3"
  val osLibVersion = "0.11.5-M10"
  val osLib = mvn"com.lihaoyi::os-lib:$osLibVersion"
  val osLibWatch = mvn"com.lihaoyi::os-lib-watch:$osLibVersion"
  val pprint = mvn"com.lihaoyi::pprint:0.9.3"
  val mainargs = mvn"com.lihaoyi::mainargs:0.7.6"
  val millModuledefsVersion = "0.11.10"
  val millModuledefsString = s"com.lihaoyi::mill-moduledefs:${millModuledefsVersion}"
  val millModuledefs = mvn"${millModuledefsString}"
  val millModuledefsPlugin =
    mvn"com.lihaoyi:::scalac-mill-moduledefs-plugin:${millModuledefsVersion}"
  val unrollAnnotation = mvn"com.lihaoyi::unroll-annotation:0.2.0"
  val unrollPlugin = mvn"com.lihaoyi::unroll-plugin:0.2.0"
  // can't use newer versions, as these need higher Java versions
  val testng = mvn"org.testng:testng:7.5.1"
  val sbtTestInterface = mvn"org.scala-sbt:test-interface:1.0"
  def scalaCompiler(scalaVersion: String) = {
    if (JvmWorkerUtil.isScala3(scalaVersion)) mvn"org.scala-lang:scala3-compiler_3:${scalaVersion}"
    else mvn"org.scala-lang:scala-compiler:${scalaVersion}"
  }
  val scalafmtDynamic = mvn"org.scalameta::scalafmt-dynamic:3.9.4".withDottyCompat(scalaVersion)
  def scalaReflect(scalaVersion: String) =
    if (JvmWorkerUtil.isScala3(scalaVersion))
      mvn"org.scala-lang:scala-reflect:${Deps.scala2Version}"
    else mvn"org.scala-lang:scala-reflect:${scalaVersion}"
  val scoverage2Version = "2.3.0"
  val scalacScoverage2Plugin = mvn"org.scoverage:::scalac-scoverage-plugin:${scoverage2Version}"
  val scalacScoverage2Reporter = mvn"org.scoverage::scalac-scoverage-reporter:${scoverage2Version}"
  val scalacScoverage2Domain = mvn"org.scoverage::scalac-scoverage-domain:${scoverage2Version}"
  val scalacScoverage2Serializer =
    mvn"org.scoverage::scalac-scoverage-serializer:${scoverage2Version}"
  val scalaparse = mvn"com.lihaoyi::scalaparse:${fastparse.version}"
  val scalatags = mvn"com.lihaoyi::scalatags:0.13.1".withDottyCompat(scalaVersion)
  val scalaXml = mvn"org.scala-lang.modules::scala-xml:2.4.0"
  // keep in sync with doc/antora/antory.yml
  val semanticDBscala = mvn"org.scalameta:::semanticdb-scalac:4.13.4"
  val semanticDbJava = mvn"com.sourcegraph:semanticdb-java:0.10.3"
  val semanticDbShared = mvn"org.scalameta:semanticdb-shared_2.13:${semanticDBscala.version}"
  val sourcecode = mvn"com.lihaoyi::sourcecode:0.4.3-M5"
  val springBootTools = mvn"org.springframework.boot:spring-boot-loader-tools:3.5.3"
  val upickle = mvn"com.lihaoyi::upickle:4.2.1"
  val upickleNamedTuples = mvn"com.lihaoyi::upickle-implicits-named-tuples:4.2.1"
  // Using "native-terminal-no-ffm" rather than just "native-terminal", as the GraalVM releases currently
  // lacks support for FFM on Mac ARM. That should be fixed soon, see oracle/graal#8113.
  val nativeTerminal = mvn"io.github.alexarchambault.native-terminal:native-terminal-no-ffm:0.0.9.1"
  val zinc = mvn"org.scala-sbt::zinc:1.10.8".withDottyCompat(scalaVersion)
  // keep in sync with doc/antora/antory.yml
  val bsp4j = mvn"ch.epfl.scala:bsp4j:2.2.0-M2"
  val gson = mvn"com.google.code.gson:gson:2.10.1"
  val fansi = mvn"com.lihaoyi::fansi:0.5.0"
  val jarjarabrams =
    mvn"com.eed3si9n.jarjarabrams::jarjar-abrams-core:1.14.1".withDottyCompat(scalaVersion)
  val requests = mvn"com.lihaoyi::requests:0.9.0"
  val logback = mvn"ch.qos.logback:logback-classic:1.5.17"
  val sonatypeCentralClient = mvn"com.lumidion::sonatype-central-client-requests:0.5.0"
  val kotlinVersion = "2.1.20"
  val kotlinCompiler = mvn"org.jetbrains.kotlin:kotlin-compiler:$kotlinVersion"

  /** Used for the `mill init` from a Maven project. */
  object MavenInit {
    val mavenVersion = "3.9.9"
    val mavenEmbedder = mvn"org.apache.maven:maven-embedder:$mavenVersion"
    val mavenResolverVersion = "1.9.22"
    val mavenResolverConnectorBasic =
      mvn"org.apache.maven.resolver:maven-resolver-connector-basic:$mavenResolverVersion"
    val mavenResolverSupplier =
      mvn"org.apache.maven.resolver:maven-resolver-supplier:$mavenResolverVersion"
    val mavenResolverTransportFile =
      mvn"org.apache.maven.resolver:maven-resolver-transport-file:$mavenResolverVersion"
    val mavenResolverTransportHttp =
      mvn"org.apache.maven.resolver:maven-resolver-transport-http:$mavenResolverVersion"
    val mavenResolverTransportWagon =
      mvn"org.apache.maven.resolver:maven-resolver-transport-wagon:$mavenResolverVersion"
  }

  val coursierJvmIndexVersion = "0.0.4-111-eb6e08"
  val gradleApi = mvn"dev.gradleplugins:gradle-api:8.11.1"

  val androidTools = mvn"com.android.tools.build:gradle:8.9.1"
  val hiltGradlePlugin = mvn"com.google.dagger:hilt-android-gradle-plugin:2.56"

  val sbt = mvn"org.scala-sbt:sbt:1.10.10"
  val snakeyamlEngine = mvn"org.snakeyaml:snakeyaml-engine:2.9"
  val spotlessLibExtra = mvn"com.diffplug.spotless:spotless-lib-extra:3.2.0"
  // JGit 6.x series, used by spotlessLibExtra, works on Java 11
  // subsequent releases require Java 17+
  val jgit = mvn"org.eclipse.jgit:org.eclipse.jgit:6.10.1.202505221210-r"

  object RuntimeDeps {
    val dokkaVersion = "2.0.0"
    val koverVersion = "0.8.3"

    val detektCli = mvn"io.gitlab.arturbosch.detekt:detekt-cli:1.23.7"
    val dokkaAnalysisDescriptors =
      mvn"org.jetbrains.dokka:analysis-kotlin-descriptors:$dokkaVersion"
    val dokkaBase = mvn"org.jetbrains.dokka:dokka-base:$dokkaVersion"
    val dokkaCli = mvn"org.jetbrains.dokka:dokka-cli:$dokkaVersion"
    val errorProneCore = mvn"com.google.errorprone:error_prone_core:2.31.0"
    val freemarker = mvn"org.freemarker:freemarker:2.3.34"
    val jupiterInterface = mvn"com.github.sbt.junit:jupiter-interface:0.13.3"
    val kotestJvm =
      mvn"io.kotest:kotest-framework-multiplatform-plugin-embeddable-compiler:5.9.1"
    val kotlinxHtmlJvm = mvn"org.jetbrains.kotlinx:kotlinx-html:0.11.0"
    val koverCli = mvn"org.jetbrains.kotlinx:kover-cli:$koverVersion"
    val koverJvmAgent = mvn"org.jetbrains.kotlinx:kover-jvm-agent:$koverVersion"
    val ktfmt = mvn"com.facebook:ktfmt:0.53"
    val ktlint = mvn"com.pinterest.ktlint:ktlint-core:0.49.1"
    val palantirFormat = mvn"com.palantir.javaformat:palantir-java-format:2.51.0"
    val proguard = mvn"com.guardsquare:proguard-base:7.7.0"
    val revApi = mvn"org.revapi:revapi-standalone:0.12.0"
    val sbtTestInterface = mvn"com.github.sbt:junit-interface:0.13.2"
    val pmdDist = mvn"net.sourceforge.pmd:pmd-dist:7.15.0"

    def updateable = Seq(
      detektCli,
      dokkaAnalysisDescriptors,
      dokkaBase,
      dokkaCli,
      errorProneCore,
      freemarker,
      jupiterInterface,
      kotestJvm,
      kotlinxHtmlJvm,
      koverCli,
      koverJvmAgent,
      ktfmt,
      ktlint,
      palantirFormat,
      proguard,
      revApi,
      sbtTestInterface,
      pmdDist
    )
  }

  /** Used to manage transitive versions. */
  lazy val transitiveDeps = Seq(
    mvn"org.apache.ant:ant:1.10.15",
    Deps.commonsIo,
    Deps.gson,
    mvn"com.google.protobuf:protobuf-java:4.29.5",
    mvn"com.google.guava:guava:33.4.0-jre",
    mvn"org.yaml:snakeyaml:2.3",
    mvn"org.apache.commons:commons-compress:1.27.1"
  )

  /** Used in tests. */
  object TestDeps {
    // tests framework (test)
    val scalaCheck = mvn"org.scalacheck::scalacheck:1.18.1"
    val scalaTest = mvn"org.scalatest::scalatest:3.2.19"
    val utest = mvn"com.lihaoyi::utest:0.9.0"
    val zioTest = mvn"dev.zio::zio-test:2.1.14"
  }

  /** Used in documentation. */
  object DocDeps {
    val millScip = mvn"io.chris-kipp::mill-scip_mill0.11:0.3.7"
  }

  object AndroidDeps {
    val manifestMerger = mvn"com.android.tools.build:manifest-merger:31.10.0"
    val bundleTool = mvn"com.android.tools.build:bundletool:1.17.2"
    val ndkVersion = "27.0.12077973"
    val cmakeVersion = "3.22.1"
    val layoutLibVersion = "15.1.2"
    val layoutLibRenderer = mvn"com.android.tools.layoutlib:layoutlib:$layoutLibVersion"
    val layoutLibRuntime = mvn"com.android.tools.layoutlib:layoutlib-runtime:$layoutLibVersion"
    val composePreviewRenderer =
      mvn"com.android.tools.compose:compose-preview-renderer-model:0.0.1-alpha09"
    val uiTooling = mvn"androidx.compose.ui:ui:1.7.6"
    val screenshotValidationJunitEngine =
      mvn"com.android.tools.screenshot:screenshot-validation-junit-engine:0.0.1-alpha09"
    val millCmdlineToolsVersion = "19.0"
    // TODO: uiTooling is needed for screenshot tests
    // so we handle it diferrently.
    // Removed it from updaetable for now
    def updateable = Seq(
      manifestMerger,
      bundleTool,
      layoutLibRenderer,
      layoutLibRuntime,
      composePreviewRenderer,
      screenshotValidationJunitEngine
    )

  }
  val keytoolDeps = Seq(
    mvn"org.bouncycastle:bcpkix-jdk18on:1.81",
    mvn"org.bouncycastle:bcprov-jdk18on:1.81",
    mvn"org.bouncycastle:bcutil-jdk18on:1.81"
  )

}
