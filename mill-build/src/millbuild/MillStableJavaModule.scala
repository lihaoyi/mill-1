package millbuild

import com.github.lolgab.mill.mima.*
import mill.*

/** Publishable module which contains strictly handled API. */
trait MillStableJavaModule extends MillPublishJavaModule with Mima {

  override def mimaBinaryIssueFilters: T[Seq[ProblemFilter]] = Seq(
    // private class
    ProblemFilter.exclude[Problem]("mill.javalib.RunModule#RunnerImpl*")
  )

  def mimaPreviousVersions: T[Seq[String]] = Settings.mimaBaseVersions

  def mimaExcludeAnnotations = Seq("mill.api.daemon.experimental")

  override def mimaReportSignatureProblems = true
}
