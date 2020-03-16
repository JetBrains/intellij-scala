package org.jetbrains.plugins.scala.util.reporter

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.util.reporter.ProgressReporter.TextBasedProgressIndicator
import org.junit.Assert

/**
  * @author mutcianm
  * @since 16.05.17.
  */
class ConsoleReporter(override val filesWithProblems: Map[String, Set[TextRange]]) extends ProgressReporter {
  private val report = new StringBuilder("\n")

  private def formatMessage(fileName: String, range: TextRange, message: String) =
    s"Error: $fileName${range.toString} - $message\n"

  override def showError(fileName: String, range: TextRange, message: String): Unit =
    report.append(formatMessage(fileName, range, message))

  override def updateHighlightingProgress(percent: Int): Unit = {
    println(s"Highlighting -  $percent%")
  }

  override def reportResults(): Unit = {
    val errorsTip = expectedErrorsTip(expectedErrors ++ unexpectedErrors)

    val noErrorsButExpected = unexpectedSuccess
    if (noErrorsButExpected.nonEmpty) {
      val reportSuccess = noErrorsButExpected.mkString(
        "Looks like you've fixed highlighting in files: \n", "\n",
        "\nRemove them from `filesWithProblems` of a test case.\n\n")
      report.append(reportSuccess)
    }

    report.append(s"Errors tip: \n$errorsTip")

    Assert.assertTrue(report.toString(), unexpectedErrors.isEmpty && noErrorsButExpected.isEmpty)

    val expected = expectedErrors.map((formatMessage _).tupled)
    if (expected.nonEmpty) {
      println(expected.mkString("\nHighlighting errors in problematic files: \n", "\n", ""))
    }
  }

  private def expectedErrorsTip(errors: Seq[(String, TextRange, String)]): String = {
    val maxErrorsPerTip = 7
    def getEntryText(fileName: String, fileErrors: Seq[(String, TextRange, String)]): String = {
      val errorsSeq =
        if (fileErrors.length > maxErrorsPerTip) ""
        else fileErrors.map(_._2).toSet.map((r: TextRange) => s"(${r.getStartOffset}, ${r.getEndOffset})").mkString(",")
      s"""  "$fileName" -> Set($errorsSeq)"""
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
