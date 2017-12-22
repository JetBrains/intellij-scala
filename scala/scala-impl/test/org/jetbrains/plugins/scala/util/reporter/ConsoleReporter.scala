package org.jetbrains.plugins.scala.util.reporter

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.util.reporter.ProgressReporter.TextBasedProgressIndicator
import org.junit.Assert

/**
  * @author mutcianm
  * @since 16.05.17.
  */
class ConsoleReporter(val filesWithProblems: Map[String, Set[TextRange]]) extends ProgressReporter {

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
    val errorsTip = expectedErrorsTip(expectedErrors ++ unexpectedErrors)
    val report = allMessages.mkString(s"Found $totalErrors errors\n\n", "\n", "")
    try {
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
    } finally {
      println(s"Errors tip: \n$errorsTip") //without this 'try-finally' wrapper the output gets lost in debug log sometimes
    }
  }

  private def expectedErrorsTip(errors: Seq[(String, TextRange, String)]): String = {
    val maxErrorsPerTip = 7
    def getEntryText(fileName: String, fileErrors: Seq[(String, TextRange, String)]): String = {
      val errorsSeq = if (fileErrors.length > maxErrorsPerTip) ""
                      else fileErrors.map(r => s"(${r._2.getStartOffset}, ${r._2.getEndOffset})").mkString(",")
      s"""  "$fileName" -> Seq($errorsSeq)"""
    }
    val errorRanges: Iterable[String] =
      for ((fileName, fileErrors) <- errors.groupBy(_._1)) yield getEntryText(fileName, fileErrors)

    s"""
       |Map(
       |  ${errorRanges.mkString(",\n")}
       |)
     """.stripMargin
  }


  override val progressIndicator: ProgressIndicator = new TextBasedProgressIndicator
  override def notify(message: String): Unit = println(message)
}
