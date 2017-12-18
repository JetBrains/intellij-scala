package org.jetbrains.plugins.scala.util.reporter

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.util.reporter.ProgressReporter.TextBasedProgressIndicator
import org.junit.Assert

/**
  * @author mutcianm
  * @since 16.05.17.
  */
class ConsoleReporter(val filesWithProblems: Set[String]) extends ProgressReporter {

  private def formatMessage(fileName: String, range: TextRange, message: String) =
    s"Error: $fileName${range.toString} - $message"

  def showError(fileName: String, range: TextRange, message: String): Unit =
    System.err.println(formatMessage(fileName, range, message))

  def updateHighlightingProgress(percent: Int): Unit = {
    println(s"Highlighting -  $percent%")
  }

  def reportResults(): Unit = {
    val allMessages = unexpectedErrors.map((formatMessage _).tupled)

    val totalErrors = allMessages.size
    val report = allMessages.mkString(s"Found $totalErrors errors\n\n", "\n", "")
    Assert.assertTrue(report, totalErrors == 0)

    val noErrorsButExpected = unexpectedSuccess
    val report2 = noErrorsButExpected.mkString(
      "Looks like you've fixed highlighting in files: \n", "\n",
      "\nRemove them from `filesWithProblems` of a test case.")
    Assert.assertTrue(report2, noErrorsButExpected.isEmpty)

    val expected = expectedErrors.map((formatMessage _).tupled)
    if (expected.nonEmpty) {
      println()
      println(expected.mkString("Highlighting errors in problematic files: \n", "\n", ""))
    }
  }


  override val progressIndicator: ProgressIndicator = new TextBasedProgressIndicator
  override def notify(message: String): Unit = println(message)
}
