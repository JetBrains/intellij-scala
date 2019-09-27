package org.jetbrains.plugins.scala.testingSupport.test.sbt

import java.util.regex.{Matcher, Pattern}

import com.intellij.execution.process.ProcessHandler
import org.jetbrains.plugins.scala.testingSupport.TestRunnerUtil
import org.jetbrains.sbt.shell.SbtShellCommunication
import org.jetbrains.sbt.shell.SbtShellCommunication.{ErrorWaitForInput, ShellEvent, TaskComplete, TaskStart}

final class SbtTestEventHandler(processHandler: ProcessHandler) extends Function1[ShellEvent, Unit] {

  private var currentId = 0
  private var idStack = List[Int](0)
  private var testCount = 0

  private val tsReporter = new TeamcityReporter(processHandler)
  import tsReporter._

  protected def openScope(matcher: Matcher, isSuite: Boolean): Unit = {
    import TestRunnerUtil.escapeString
    currentId += 1
    reportTestStarted(
      name = escapeString(matcher.group(if (isSuite) 1 else 2)),
      nodeId = currentId,
      parentNodeId = idStack.head
    )
    idStack = currentId :: idStack
  }

  protected def closeScope(matcher: Matcher, isSuite: Boolean): Unit = {
    import TestRunnerUtil.escapeString
    val suiteId = idStack.head
    idStack = idStack.tail
    reportTestFinished(
      name = escapeString(matcher.group(if (isSuite) 1 else 2)),
      nodeId = suiteId,
      parentNodeId = idStack.head,
      duration = if (isSuite) Some(SbtTestEventHandler.extractDurationMs(matcher.group(2))) else None
    )
  }

  def closeRoot(): Unit = processHandler.destroyProcess()

  override def apply(se: ShellEvent): Unit = se match {
    case TaskStart =>
    case TaskComplete =>
    case ErrorWaitForInput => throw new Exception("error running sbt")
    case SbtShellCommunication.Output(output) =>
      import SbtTestEventHandler._
      import TestRunnerUtil._
      val infoIdx = output.indexOf(SbtInfo)
      if (infoIdx == -1) return
      val info = output.substring(infoIdx).trim

      for (regex <- regexes) {
        val matcher = regex.matcher(info)
        if (matcher.matches) {
          regex match {
            case `testStartRegex` =>
              currentId += 1
              testCount += 1
              reportTestStarted(
                name = escapeString(matcher.group(2)),
                nodeId = currentId,
                parentNodeId = idStack.head
              )
            case `testSuccessfulRegex` =>
              reportTestFinished(
                name = escapeString(matcher.group(2)),
                nodeId = currentId,
                parentNodeId = idStack.head,
                duration = Some(extractDurationMs(matcher.group(3)))
              )
            case `testFailedRegex` =>
              //TODO: add duration here
              //TODO: add message here
              val reasonAndDuration = matcher.group(3)
              val durationIndex = reasonAndDuration.lastIndexOf('(')
              val failedMessage = reasonAndDuration.substring(0, durationIndex - 1)
              reportTestFailure(
                name = escapeString(matcher.group(2)),
                nodeId = currentId,
                parentNodeId = idStack.head,
                duration = Some(extractDurationMs(reasonAndDuration.substring(durationIndex + 1, reasonAndDuration.length - 1))),
                message = escapeString(failedMessage),
                extraAttrs = actualExpectedAttrsScalaTest(failedMessage)
              )
            case `suiteStartRegex` =>
              openScope(matcher, isSuite = true)
            case `suiteFinishedRegex` =>
              closeScope(matcher, isSuite = true)
            case `testPendingRegex` =>
              reportTestIgnored(
                name = escapeString(matcher.group(2)),
                nodeId = currentId,
                parentNodeId = idStack.head,
                message = escapeString("Test Pending")
              )
            case `testIgnoredRegex` =>
              currentId += 1
              val testName = escapeString(matcher.group(2) + " !!! IGNORED !!!")
              reportTestStarted(testName, currentId, idStack.head)
              //TODO add message here
              reportTestIgnored(testName, currentId, idStack.head, TestRunnerUtil.escapeString("Test Ignored"))
            case `scopeOpenedRegex` =>
              openScope(matcher, isSuite = false)
            case `scopeClosedRegex` =>
              closeScope(matcher, isSuite = false)
          }
        }
      }
  }
}

object SbtTestEventHandler {

  private val SbtInfo = "[info]"

  private val timePattern        : Pattern = Pattern.compile("""((\d+) hour(s?), )?((\d+) minute(s?), )?((\d+) second(s?), )?(\d+) millisecond(s?)""")
  private val testStartRegex     : Pattern = Pattern.compile("""\[info\] Test Starting - ([^:]+): (.+)""")
  private val testSuccessfulRegex: Pattern = Pattern.compile("""\[info\] Test Succeeded - ([^:]+): ([^\(]+) \(([^\)]+)\)""")
  private val testFailedRegex    : Pattern = Pattern.compile("""\[info\] TEST FAILED - ([^:]+): ([^:]+): (.+)""")
  private val suiteStartRegex    : Pattern = Pattern.compile("""\[info\] Suite Starting - (.+)""")
  private val suiteFinishedRegex : Pattern = Pattern.compile("""\[info\] Suite Completed - ([^\(]+) \(([^\)]+)\)""")
  private val scopeOpenedRegex   : Pattern = Pattern.compile("""\[info\] Scope Opened - ([^:]+): (.+)""")
  private val scopeClosedRegex   : Pattern = Pattern.compile("""\[info\] Scope Closed - ([^:]+): (.+)""")
  private val testPendingRegex   : Pattern = Pattern.compile("""\[info\] Test Pending - ([^:]+): (.+)""")
  private val testIgnoredRegex   : Pattern = Pattern.compile("""\[info\] Test Ignored - ([^:]+): (.+)""")

// TODO maybe parse failed test location later (info from sbt output is not enough anyway)
//  def failedLocationRegex(failureMessage: String): Pattern = Pattern.compile(s"\\[info\\] ([\\s]+)$failureMessage \\(([^\\)]+)\\)")

  private val regexes: List[Pattern] = List(
    testStartRegex, testSuccessfulRegex, testFailedRegex,
    suiteStartRegex, suiteFinishedRegex,
    scopeOpenedRegex, scopeClosedRegex,
    testPendingRegex, testIgnoredRegex
  )

  private def extractDurationMs(duration: String): Long = {
    val durationMatcher = timePattern.matcher(duration)
    if (durationMatcher.matches()) {
      val milliseconds = durationMatcher.group(10).toLong
      val seconds      = opt(durationMatcher.group(8))
      val minutes      = opt(durationMatcher.group(5))
      val hours        = opt(durationMatcher.group(2))
      milliseconds + 1000 * (seconds + 60 * (minutes + 60 * hours))
    } else {
      0
    }
  }

  private def opt(string: String): Long = Option(string).fold(0L)(_.toLong)
}
