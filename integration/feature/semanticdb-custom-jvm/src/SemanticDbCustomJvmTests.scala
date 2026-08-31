package mill.integration

import mill.constants.EnvVars
import mill.testkit.UtestIntegrationTestSuite
import utest.*

import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import scala.util.Using

object SemanticDbCustomJvmTests extends UtestIntegrationTestSuite {
  override def allowSharedOutputDir: Boolean = false

  val tests: Tests = Tests {
    test("workerRelativeSourceRoot") {
      def check(noDaemon: Boolean) = integrationTest { tester =>
        val command = Option.when(noDaemon)("--no-daemon").toSeq :+ "__.semanticDbData"
        val result = tester.eval(command)
        assert(result.isSuccess)

        for (module <- Seq("scala2", "scala3")) {
          val semanticDb = tester.workspacePath / "out" / module /
            "semanticDbDataDetailed.dest/data/META-INF/semanticdb" / module / "src/Main.scala.semanticdb"
          assert(
            os.exists(semanticDb),
            !os.exists(tester.workspacePath / module / "src/Main.scala.semanticdb")
          )
        }
      }

      test("daemon") - check(noDaemon = false)
      test("noDaemon") - check(noDaemon = true)
    }

    test("semanticdb survives workspace relocation") - integrationTest { tester =>
      val originalWorkspace = tester.workspacePath
      val relocationRoot = os.temp.dir(prefix = "mill-semanticdb-relocation-")
      val relocatedWorkspace = relocationRoot / "deeper" / "workspace"
      val noDaemonArg = Option.when(tester.daemonMode)("--no-daemon").toSeq

      val first = tester.eval(noDaemonArg :+ "scala2.semanticDbData")
      assert(first.isSuccess)

      os.makeDir.all(relocatedWorkspace / os.up)
      os.move(originalWorkspace, relocatedWorkspace)

      try {
        os.write.append(
          relocatedWorkspace / "scala2/src/Main.scala",
          "\nobject ChangedAfterRelocation\n"
        )

        val second = tester.eval(
          noDaemonArg :+ "scala2.semanticDbData",
          cwd = relocatedWorkspace,
          env = Map(EnvVars.MILL_WORKSPACE_ROOT -> relocatedWorkspace.toString)
        )
        val output = second.out + "\n" + second.err
        val semanticDb = relocatedWorkspace /
          "out/scala2/semanticDbDataDetailed.dest/data/META-INF/semanticdb/scala2/src/Main.scala.semanticdb"
        assert(
          second.isSuccess,
          output.contains("compiling 1 Scala source"),
          os.exists(semanticDb),
          !os.exists(relocatedWorkspace / "scala2/src/Main.scala.semanticdb")
        )

        val zincFile = relocatedWorkspace / "out/scala2/semanticDbDataDetailed.dest/zinc"
        val zincBytes = Using.resource(
          new GZIPInputStream(os.read.inputStream(zincFile))
        )(_.readAllBytes())
        val zincContents = String(zincBytes, StandardCharsets.ISO_8859_1).replace('\\', '/')
        val originalWorkspaceString = originalWorkspace.toString.replace('\\', '/')
        assert(
          zincContents.contains("../../../scala2/src/Main.scala"),
          !zincContents.contains(originalWorkspaceString)
        )
      } finally {
        if (os.exists(relocatedWorkspace)) {
          os.remove.all(originalWorkspace)
          os.move(relocatedWorkspace, originalWorkspace)
        }
        os.remove.all(relocationRoot)
      }
    }
  }
}
