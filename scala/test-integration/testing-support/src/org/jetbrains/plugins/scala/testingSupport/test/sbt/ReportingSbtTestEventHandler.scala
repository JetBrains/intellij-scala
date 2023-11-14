package org.jetbrains.plugins.scala.testingSupport.test.sbt

import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.scala.testingSupport.TestRunnerUtil
import org.jetbrains.plugins.scala.testingSupport.test.sbt.ReportingSbtTestEventHandler.TeamCityTestStatusReporter
import org.jetbrains.sbt.shell.SbtShellCommunication
import org.jetbrains.sbt.shell.SbtShellCommunication.{ErrorWaitForInput, ProcessTerminated, ShellEvent, TaskComplete, TaskStart}

import java.util.regex.{Matcher, Pattern}

@ApiStatus.Internal
trait SbtTestEventHandler {

  def processEvent(event: ShellEvent): Unit
}

@ApiStatus.Internal
class ReportingSbtTestEventHandler(messageConsumer: TeamCityTestStatusReporter)
  extends SbtTestEventHandler {

  import ReportingSbtTestEventHandler._

  private var currentId = 0
  private var idStack = List[Int](0)
  private var testCount = 0

  override def processEvent(se: ShellEvent): Unit = se match {
    case TaskStart =>
    case TaskComplete =>
    case ProcessTerminated => throw new Exception("sbt process terminated")
    case ErrorWaitForInput => throw new Exception("error running sbt")
    case SbtShellCommunication.Output(output) =>
      import TestRunnerUtil._
      val infoIdx = output.indexOf(sbtInfo)
      if (infoIdx == -1) return
      val info = output.substring(infoIdx).trim
      for (regex <- regexes) {
        val matcher = regex.matcher(info)
        if (matcher.matches) {
          regex match {
            case `testStartRegex` =>
              currentId += 1
              testCount += 1
              report(s"\n##teamcity[testStarted name='${escapeString(matcher.group(2))}' nodeId='$currentId' " +
                s"parentNodeId='${idStack.head}' captureStandardOutput='true']\n")
            case `testSuccessfulRegex` =>
              report(s"\n##teamcity[testFinished name='${escapeString(matcher.group(2))}' nodeId='$currentId' " +
                s"parentNodeId='${idStack.head}' duration='${getDuration(matcher.group(3))}']\n")
            case `testFailedRegex` =>
              //TODO: add duration here
              //TODO: add message here
              val reasonAndDuration = matcher.group(3)
              val durationIndex = reasonAndDuration.lastIndexOf('(')
              val failedMessage = reasonAndDuration.substring(0, durationIndex - 1)
              report(s"\n##teamcity[testFailed name='${escapeString(matcher.group(2))}' nodeId='$currentId' " +
                s"parentNodeId='${idStack.head}' message='${escapeString(failedMessage)}'" +
                s"${actualExpectedAttrsScalaTest(failedMessage)}" +
                s"duration='${getDuration(reasonAndDuration.substring(durationIndex + 1, reasonAndDuration.length - 1))}']\n")
            case `suiteStartRegex` =>
              openScope(matcher, isSuite = true)
            case `suiteFinishedRegex` =>
              closeScope(matcher, isSuite = true)
            case `testPendingRegex` =>
              report(s"\n##teamcity[testIgnored name='${escapeString(matcher.group(2))}' nodeId='$currentId' " +
                s"parentNodeId='${idStack.head}' message='${escapeString("Test Pending")}']\n")
            case `testIgnoredRegex` =>
              currentId += 1
              val testName = escapeString(matcher.group(2) + " !!! IGNORED !!!")
              report(s"\n##teamcity[testStarted name='$testName' nodeId='$currentId' parentNodeId='${idStack.head}']\n")
              //TODO add message here
              report(s"\n##teamcity[testIgnored name='$testName' nodeId='$currentId' parentNodeId='${idStack.head}' " +
                s"message='${TestRunnerUtil.escapeString("Test Ignored")}']\n")
            case `scopeOpenedRegex` =>
              openScope(matcher, isSuite = false)
            case `scopeClosedRegex` =>
              closeScope(matcher, isSuite = false)
          }
        }
      }
  }

  protected def report(message: String): Unit =
    messageConsumer.report(message, ReportingSbtTestEventHandler.processOutputType)

  protected def openScope(matcher: Matcher, isSuite: Boolean): Unit = {
    import TestRunnerUtil.escapeString
    currentId += 1
    report(s"\n##teamcity[testSuiteStarted name='${escapeString(matcher.group(if (isSuite) 1 else 2))}' " +
      s"nodeId='$currentId' parentNodeId='${idStack.head}' captureStandardOutput='true']\n")
    idStack = currentId :: idStack
  }

  protected def closeScope(matcher: Matcher, isSuite: Boolean): Unit = {
    import TestRunnerUtil.escapeString
    val suiteId = idStack.head
    idStack = idStack.tail
    report(s"\n##teamcity[testSuiteFinished name='${escapeString(matcher.group(if (isSuite) 1 else 2))}' " +
      s"nodeId='$suiteId' parentNodeId='${idStack.head}'" +
      (if (isSuite) s" duration='${ReportingSbtTestEventHandler.getDuration(matcher.group(2))}'" else "") + "]\n")
  }

}

object ReportingSbtTestEventHandler {

  val timePattern: Pattern = Pattern.compile("((\\d+) hour(s?), )?((\\d+) minute(s?), )?((\\d+) second(s?), )?(\\d+) millisecond(s?)")

  val testStartRegex: Pattern = Pattern.compile("\\[info\\] Test Starting - ([^:]+): (.+)")
  val testSuccessfulRegex: Pattern = Pattern.compile("\\[info\\] Test Succeeded - ([^:]+): ([^\\(]+) \\(([^\\)]+)\\)")
  val testFailedRegex: Pattern = Pattern.compile("\\[info\\] TEST FAILED - ([^:]+): ([^:]+): (.+)")
  val suiteStartRegex: Pattern = Pattern.compile("\\[info\\] Suite Starting - (.+)")
  val suiteFinishedRegex: Pattern = Pattern.compile("\\[info\\] Suite Completed - ([^\\(]+) \\(([^\\)]+)\\)")
  val scopeOpenedRegex: Pattern = Pattern.compile("\\[info\\] Scope Opened - ([^:]+): (.+)")
  val scopeClosedRegex: Pattern = Pattern.compile("\\[info\\] Scope Closed - ([^:]+): (.+)")
  val testPendingRegex: Pattern = Pattern.compile("\\[info\\] Test Pending - ([^:]+): (.+)")
  val testIgnoredRegex: Pattern = Pattern.compile("\\[info\\] Test Ignored - ([^:]+): (.+)")

// TODO maybe parse failed test location later (info from sbt output is not enough anyway)
//  def failedLocationRegex(failureMessage: String): Pattern = Pattern.compile(s"\\[info\\] ([\\s]+)$failureMessage \\(([^\\)]+)\\)")

  val regexes: List[Pattern] = List(testStartRegex, testSuccessfulRegex, testFailedRegex, suiteStartRegex,
    suiteFinishedRegex, scopeOpenedRegex, scopeClosedRegex, testPendingRegex, testIgnoredRegex)

  val processOutputType: Key[_] = ProcessOutputTypes.STDOUT

  def getDuration(duration: String): Long = {
    val durationMatcher = timePattern.matcher(duration)
    if (!durationMatcher.matches()) return 0
    durationMatcher.group(10).toLong + 1000 *
      (opt(durationMatcher.group(8)) + 60 *
        (opt(durationMatcher.group(5)) + 60 * opt(durationMatcher.group(2))))
  }

  def opt(string: String): Long = Option(string).map(_.toLong).getOrElse(0L)

  val sbtInfo = "[info]"

  trait TeamCityTestStatusReporter {
    /**
     * @param message test status reprot message in TeamCity format
     */
    def report(message: String, key: Key[_]): Unit
  }
}
