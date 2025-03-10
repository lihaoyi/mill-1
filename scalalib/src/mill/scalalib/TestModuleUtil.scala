package mill.scalalib

import mill.api.{Ctx, PathRef, Result}
import mill.constants.EnvVars
import mill.testrunner.{TestArgs, TestResult, TestRunnerUtils}
import mill.util.Jvm
import mill.Task
import sbt.testing.Status

import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.{Instant, LocalDateTime, ZoneId}
import scala.xml.Elem
import scala.collection.mutable
import mill.api.Logger
import java.util.concurrent.Executors

private final class TestModuleUtil(
    useArgsFile: Boolean,
    forkArgs: Seq[String],
    selectors: Seq[String],
    scalalibClasspath: Seq[PathRef],
    resources: Seq[PathRef],
    testFramework: String,
    runClasspath: Seq[PathRef],
    testClasspath: Seq[PathRef],
    args: Seq[String],
    testClassLists: Seq[Seq[String]],
    testrunnerEntrypointClasspath: Seq[PathRef],
    forkEnv: Map[String, String],
    testSandboxWorkingDir: Boolean,
    forkWorkingDir: os.Path,
    testReportXml: Option[String],
    javaHome: Option[os.Path],
    testEnableWorkStealing: Boolean
)(implicit ctx: mill.api.Ctx) {

  private val (jvmArgs, props: Map[String, String]) =
    TestModuleUtil.loadArgsAndProps(useArgsFile, forkArgs)

  private val testRunnerClasspathArg = scalalibClasspath
    .map(_.path.toNIO.toUri.toURL)
    .mkString(",")

  private val resourceEnv = Map(
    EnvVars.MILL_TEST_RESOURCE_DIR -> resources.map(_.path).mkString(";"),
    EnvVars.MILL_WORKSPACE_ROOT -> Task.workspace.toString
  )

  def runTests(): Result[(String, Seq[TestResult])] = {
    val globFilter = TestRunnerUtils.globFilter(selectors)

    def doesNotMatchError = new Result.Exception(
      s"Test selector does not match any test: ${selectors.mkString(" ")}" +
        "\nRun discoveredTestClasses to see available tests"
    )

    val filteredClassLists0 = testClassLists.map(_.filter(globFilter)).filter(_.nonEmpty)

    val filteredClassLists =
      if (filteredClassLists0.size == 1) filteredClassLists0
      else {
        // If test grouping is enabled and multiple test groups are detected, we need to
        // run test discovery via the test framework's own argument parsing and filtering
        // logic once before we potentially fork off multiple test groups that will
        // each do the same thing and then run tests. This duplication is necessary so we can
        // skip test groups that we know will be empty, which is important because even an empty
        // test group requires spawning a JVM which can take 1+ seconds to realize there are no
        // tests to run and shut down

        val discoveredTests = if (javaHome.isDefined) {
          Jvm.callProcess(
            mainClass = "mill.testrunner.GetTestTasksMain",
            classPath = scalalibClasspath.map(_.path).toVector,
            mainArgs =
              (runClasspath ++ testrunnerEntrypointClasspath).flatMap(p =>
                Seq("--runCp", p.path.toString)
              ) ++
                testClasspath.flatMap(p => Seq("--testCp", p.path.toString)) ++
                Seq("--framework", testFramework) ++
                selectors.flatMap(s => Seq("--selectors", s)) ++
                args.flatMap(s => Seq("--args", s)),
            javaHome = javaHome,
            stdin = os.Inherit,
            stdout = os.Pipe,
            cwd = Task.dest
          ).out.lines().toSet
        } else {
          mill.testrunner.GetTestTasksMain.main0(
            (runClasspath ++ testrunnerEntrypointClasspath).map(_.path),
            testClasspath.map(_.path),
            testFramework,
            selectors,
            args
          ).toSet
        }

        filteredClassLists0.map(_.filter(discoveredTests)).filter(_.nonEmpty)
      }
    if (selectors.nonEmpty && filteredClassLists.isEmpty) throw doesNotMatchError

    val result = if (testEnableWorkStealing) {
      runTestStealingScheduler(filteredClassLists)
    } else {
      runTestDefault(filteredClassLists)
    }

    result match {
      case Result.Failure(errMsg) => Result.Failure(errMsg)
      case Result.Success((doneMsg, results)) =>
        if (results.isEmpty && selectors.nonEmpty) throw doesNotMatchError
        try TestModuleUtil.handleResults(doneMsg, results, Task.ctx(), testReportXml)
        catch {
          case e: Throwable => Result.Failure("Test reporting failed: " + e)
        }
    }
  }

  private def callTestRunnerSubprocess(
      baseFolder: os.Path,
      // either:
      // - Left(selectors: Seq[String]): - list of glob selectors to feed to the test runner directly.
      // - Right((testClassesFolder: os.Path, stealFolder: os.Path)): - folder containing test classes for test runner to steal from, and the stealer's base folder.
      selector: Either[Seq[String], (os.Path, os.Path)]
  )(implicit ctx: mill.api.Ctx) = {
    os.makeDir.all(baseFolder)

    val outputPath = baseFolder / "out.json"
    val testArgs = TestArgs(
      framework = testFramework,
      classpath = runClasspath.map(_.path),
      arguments = args,
      sysProps = props,
      outputPath = outputPath,
      colored = Task.log.prompt.colored,
      testCp = testClasspath.map(_.path),
      globSelectors = selector
    )

    val argsFile = baseFolder / "testargs"
    val sandbox = baseFolder / "sandbox"
    os.write(argsFile, upickle.default.write(testArgs), createFolders = true)

    os.makeDir.all(sandbox)

    os.checker.withValue(os.Checker.Nop) {
      Jvm.callProcess(
        mainClass = "mill.testrunner.entrypoint.TestRunnerMain",
        classPath = (runClasspath ++ testrunnerEntrypointClasspath).map(_.path),
        jvmArgs = jvmArgs,
        env = forkEnv ++ resourceEnv,
        mainArgs = Seq(testRunnerClasspathArg, argsFile.toString),
        cwd = if (testSandboxWorkingDir) sandbox else forkWorkingDir,
        cpPassingJarPath = Option.when(useArgsFile)(
          os.temp(prefix = "run-", suffix = ".jar", deleteOnExit = false)
        ),
        javaHome = javaHome,
        stdin = os.Inherit,
        stdout = os.Inherit
      )
    }

    if (!os.exists(outputPath))
      Result.Failure(s"Test reporting Failed: ${outputPath} does not exist")
    else
      Result.Success(upickle.default.read[(String, Seq[TestResult])](ujson.read(outputPath.toIO)))
  }

  private def runTestDefault(
      filteredClassLists: Seq[Seq[String]]
  )(implicit ctx: mill.api.Ctx) = {

    val subprocessResult: Result[(String, Seq[TestResult])] = filteredClassLists match {
      // When no tests at all are discovered, run at least one test JVM
      // process to go through the test framework setup/teardown logic
      case Nil => callTestRunnerSubprocess(Task.dest, Left(Nil))
      case Seq(singleTestClassList) =>
        callTestRunnerSubprocess(Task.dest, Left(singleTestClassList))
      case multipleTestClassLists =>
        val maxLength = multipleTestClassLists.length.toString.length
        val futures = multipleTestClassLists.zipWithIndex.map { case (testClassList, i) =>
          val groupPromptMessage = testClassList match {
            case Seq(single) => single
            case multiple =>
              TestModuleUtil.collapseTestClassNames(
                multiple
              ).mkString(", ") + s", ${multiple.length} suites"
          }

          val paddedIndex = mill.internal.Util.leftPad(i.toString, maxLength, '0')
          val folderName = testClassList match {
            case Seq(single) => single
            case multiple =>
              s"group-$paddedIndex-${multiple.head}"
          }

          Task.fork.async(Task.dest / folderName, paddedIndex, groupPromptMessage) {
            (folderName, callTestRunnerSubprocess(Task.dest / folderName, Left(testClassList)))
          }
        }

        val outputs = Task.fork.awaitAll(futures)

        val (lefts, rights) = outputs.partitionMap {
          case (name, Result.Failure(v)) => Left(name + " " + v)
          case (name, Result.Success((msg, results))) => Right((name + " " + msg, results))
        }

        if (lefts.nonEmpty) Result.Failure(lefts.mkString("\n"))
        else Result.Success((rights.map(_._1).mkString("\n"), rights.flatMap(_._2)))
    }

    subprocessResult
  }

  private def runTestStealingScheduler(
      filteredClassLists: Seq[Seq[String]]
  )(implicit ctx: mill.api.Ctx) = {

    val workerStatusMap = new java.util.concurrent.ConcurrentHashMap[os.Path, String => Unit]()

    def prepareTestClassesFolder(selectors2: Seq[String], base: os.Path): os.Path = {
      // test-classes folder is used to store the test classes for the children test runners to steal from
      val testClassesFolder = base / "test-classes"
      os.makeDir.all(testClassesFolder)
      selectors2.zipWithIndex.foreach { case (s, i) =>
        os.write.over(testClassesFolder / s, Array.empty[Byte])
      }
      testClassesFolder
    }

    def runTestRunnerSubprocess(
        base: os.Path,
        testClassesFolder: os.Path,
        force: Boolean,
        logger: Logger
    ) = {
      // Check if we really need to spawn a new runner
      if (force || os.list(testClassesFolder).nonEmpty) {
        val stealFolder = base / "steal"
        os.makeDir.all(stealFolder)
        // steal.log file will be appended by the runner with the stolen test class's name
        // it can be used to check the order of test classes of the runner
        val stealLog = stealFolder / os.up / s"${stealFolder.last}.log"
        os.write.over(stealLog, Array.empty[Byte])
        workerStatusMap.put(stealLog, logger.ticker)
        val result = callTestRunnerSubprocess(base, Right(testClassesFolder, stealFolder))
        workerStatusMap.remove(stealLog)
        Some(result)
      } else {
        None
      }
    }

    val groupFolderData = filteredClassLists match {
      case Nil => Seq((Task.dest, prepareTestClassesFolder(Nil, Task.dest), 0))
      case Seq(singleTestClassList) =>
        Seq((
          Task.dest,
          prepareTestClassesFolder(singleTestClassList, Task.dest),
          singleTestClassList.length
        ))
      case multipleTestClassLists =>
        val maxLength = multipleTestClassLists.length.toString.length
        multipleTestClassLists.zipWithIndex.map { case (testClassList, i) =>
          val paddedIndex = mill.internal.Util.leftPad(i.toString, maxLength, '0')
          val folderName = testClassList match {
            case Seq(single) => single
            case multiple =>
              s"group-$paddedIndex-${multiple.head}"
          }

          (
            Task.dest / folderName,
            prepareTestClassesFolder(testClassList, Task.dest / folderName),
            testClassList.length
          )
        }
    }


    def jobsProcessLength(numTests: Int) = Task.ctx() match {
      case j: Ctx.Jobs =>
        val cappedJobs = Math.min(j.jobs, numTests)
        (cappedJobs, Math.max(cappedJobs, 1))
      case _ => (1, 1)
    }

    val groupLength = groupFolderData.length
    val maxGroupLength = groupLength.toString.length

    // We got "--jobs" threads, and "groupLength" test groups, so we will spawn at most jobs * groupLength runners here
    // In most case, this is more than necessary, and runner creation is expensive,
    // but we have a check for non-empty test-classes folder before really spawning a new runner, so in practice the overhead is low
    val subprocessFutures = for {
      ((groupFolder, testClassesFolder, numTests), groupIndex) <- groupFolderData.zipWithIndex
      // Don't have re-calculate for every processes
      groupName = groupFolder.last
      (jobs, maxProcessLength) = jobsProcessLength(numTests)
      paddedGroupIndex = mill.internal.Util.leftPad(groupIndex.toString, maxGroupLength, '0')
      processIndex <- 0 until Math.max(Math.min(jobs, numTests), 1)
    } yield {

      val paddedProcessIndex =
        mill.internal.Util.leftPad(processIndex.toString, maxProcessLength, '0')
      val processFolder = groupFolder / s"worker-$paddedProcessIndex"

      val label = if(groupFolderData.size == 1) paddedProcessIndex else s"${paddedGroupIndex}-${paddedProcessIndex}"
      Task.fork.async(processFolder, label, s"worker-$label") { logger =>
        // force run when processIndex == 0 (first subprocess), even if there are no tests to run
        // to force the process to go through the test framework setup/teardown logic
        groupName -> runTestRunnerSubprocess(
          processFolder,
          testClassesFolder,
          force = processIndex == 0,
          logger
        )
      }
    }

    val executor = Executors.newScheduledThreadPool(1)
    val outputs =
      try {
        // Periodically check the stealLog file of every runner, and tick the executing test name
        executor.scheduleWithFixedDelay(
          () => {
            workerStatusMap.forEach { case (stealLog, callback) =>
              try {
                // the last one is always the latest
                os.read.lines(stealLog).lastOption.foreach(callback)
              } finally ()
            }
          },
          0,
          1,
          java.util.concurrent.TimeUnit.SECONDS
        )

        Task.fork.awaitAll(subprocessFutures)
      } finally {
        executor.shutdown()
      }

    val subprocessResult = {
      val failMap = mutable.Map.empty[String, String]
      val successMap = mutable.Map.empty[String, (String, Seq[TestResult])]

      outputs.foreach {
        case (name, Some(Result.Failure(v))) => failMap.updateWith(name) {
            case Some(old) => Some(old + " " + v)
            case None => Some(v)
          }
        case (name, Some(Result.Success((msg, results)))) => successMap.updateWith(name) {
            case Some((oldMsg, oldResults)) => Some((oldMsg + " " + msg, oldResults ++ results))
            case None => Some((msg, results))
          }
        case _ => ()
      }

      if (failMap.nonEmpty) {
        Result.Failure(failMap.values.mkString("\n"))
      } else {
        Result.Success((
          successMap.values.map(_._1).mkString("\n"),
          successMap.values.flatMap(_._2).toSeq
        ))
      }
    }

    subprocessResult
  }

}

private[scalalib] object TestModuleUtil {

  private def loadArgsAndProps(useArgsFile: Boolean, forkArgs: Seq[String]) = {
    if (useArgsFile) {
      val (props, jvmArgs) = forkArgs.partition(_.startsWith("-D"))
      val sysProps =
        props
          .map(_.drop(2).split("[=]", 2))
          .map {
            case Array(k, v) => k -> v
            case Array(k) => k -> ""
          }
          .toMap

      jvmArgs -> sysProps
    } else {
      forkArgs -> Map()
    }
  }

  private[scalalib] def handleResults(
      doneMsg: String,
      results: Seq[TestResult],
      ctx: Option[Ctx.Env]
  ): Result[(String, Seq[TestResult])] = {

    val badTests: Seq[TestResult] =
      results.filter(x => Set("Error", "Failure").contains(x.status))
    if (badTests.isEmpty) {
      Result.Success((doneMsg, results))
    } else {
      val reportCount =
        if (ctx.fold(false)(_.env.contains("CI"))) badTests.length
        else FailedTestReportCount
      val suffix =
        if (badTests.length <= reportCount) ""
        else s"\n  and ${badTests.length - reportCount} more ..."

      val msg = s"${badTests.size} tests failed: ${badTests
          .take(reportCount)
          .map(t => s"${t.fullyQualifiedName} ${t.selector}")
          .mkString("\n  ", "\n  ", "")}$suffix"

      Result.Failure(msg)
    }
  }

  private[scalalib] def handleResults(
      doneMsg: String,
      results: Seq[TestResult],
      ctx: Ctx.Env & Ctx.Dest,
      testReportXml: Option[String],
      props: Option[Map[String, String]] = None
  ): Result[(String, Seq[TestResult])] = {
    for {
      fileName <- testReportXml
      path = ctx.dest / fileName
      xml <- genTestXmlReport(results, Instant.now(), props.getOrElse(Map.empty))
      _ = scala.xml.XML.save(path.toString(), xml, xmlDecl = true)
    } yield ()
    handleResults(doneMsg, results, Some(ctx))
  }

  private val FailedTestReportCount = 5
  private val ErrorStatus = Status.Error.name()
  private val FailureStatus = Status.Failure.name()
  private val SkippedStates =
    Set(Status.Ignored.name(), Status.Skipped.name(), Status.Pending.name())

  private[scalalib] def genTestXmlReport(
      results0: Seq[TestResult],
      timestamp: Instant,
      props: Map[String, String]
  ): Option[Elem] = {
    def durationAsString(value: Long) = (value / 1000d).toString
    def testcaseName(testResult: TestResult) =
      testResult.selector.replace(s"${testResult.fullyQualifiedName}.", "")

    def properties: Elem = {
      val ps = props.map { case (key, value) =>
        <property name={key} value={value}/>
      }
      <properties>
        {ps}
      </properties>
    }

    val suites = results0.groupBy(_.fullyQualifiedName).map { case (fqn, testResults) =>
      val cases = testResults.map { testResult =>
        val testName = testcaseName(testResult)
        <testcase classname={testResult.fullyQualifiedName}
                  name={testName}
                  time={durationAsString(testResult.duration)}>
          {testCaseStatus(testResult).orNull}
        </testcase>
      }

      <testsuite name={fqn}
                 tests={testResults.length.toString}
                 failures={testResults.count(_.status == FailureStatus).toString}
                 errors={testResults.count(_.status == ErrorStatus).toString}
                 skipped={
        testResults.count(testResult => SkippedStates.contains(testResult.status)).toString
      }
                 time={durationAsString(testResults.map(_.duration).sum)}
                 timestamp={formatTimestamp(timestamp)}>
        {properties}
        {cases}
      </testsuite>
    }
    // todo add the parent module name
    val xml =
      <testsuites tests={results0.size.toString}
                  failures={results0.count(_.status == FailureStatus).toString}
                  errors={results0.count(_.status == ErrorStatus).toString}
                  skipped={
        results0.count(testResult => SkippedStates.contains(testResult.status)).toString
      }
                  time={durationAsString(results0.map(_.duration).sum)}>
        {suites}
      </testsuites>
    if (results0.nonEmpty) Some(xml) else None
  }

  private def formatTimestamp(timestamp: Instant): String = {
    DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(
      LocalDateTime.ofInstant(
        timestamp.truncatedTo(ChronoUnit.SECONDS),
        ZoneId.of("UTC")
      )
    )
  }

  private def testCaseStatus(e: TestResult): Option[Elem] = {
    val trace: String = e.exceptionTrace.map(stackTraceTrace =>
      stackTraceTrace.map(t =>
        s"${t.getClassName}.${t.getMethodName}(${t.getFileName}:${t.getLineNumber})"
      )
        .mkString(
          s"${e.exceptionName.getOrElse("")}: ${e.exceptionMsg.getOrElse("")}\n    at ",
          "\n    at ",
          ""
        )
    ).getOrElse("")
    e.status match {
      case ErrorStatus if (e.exceptionMsg.isDefined && e.exceptionName.isDefined) =>
        Some(<error message={e.exceptionMsg.get} type={e.exceptionName.get}>
          {trace}
        </error>)
      case ErrorStatus => Some(<error message="No Exception or message provided"/>)
      case FailureStatus if (e.exceptionMsg.isDefined && e.exceptionName.isDefined) =>
        Some(<failure message={e.exceptionMsg.get} type={e.exceptionName.get}>
          {trace}
        </failure>)
      case FailureStatus => Some(<failure message="No Exception or message provided"/>)
      case s if SkippedStates.contains(s) => Some(<skipped/>)
      case _ => None
    }
  }

  /**
   * Shorten the long list of fully qualified class names by truncating
   * repetitive segments so we can see more stuff on a single line
   */
  def collapseTestClassNames(names0: Seq[String]): Seq[String] = {
    val names = names0.sorted
    Seq(names.head) ++ names.sliding(2).map {
      case Seq(prev, next) =>
        val prevSegments = prev.split('.')
        val nextSegments = next.split('.')

        nextSegments
          .zipWithIndex
          .map { case (s, i) => if (prevSegments.lift(i).contains(s)) s.head else s }
          .mkString(".")
    }
  }
}
