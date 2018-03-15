package org.jetbrains.plugins.scala.util.reporter

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.util.reporter.ProgressReporter.TextBasedProgressIndicator

/**
  * @author mutcianm
  * @since 16.05.17.
  */
class TeamCityReporter(name: String, val filesWithProblems: Map[String, Set[TextRange]], reportSuccess: Boolean) extends ProgressReporter {
  import TeamCityReporter._

  override def updateHighlightingProgress(percent: Int): Unit = progressMessage(s"Highlighting - $percent%")

  override def showError(fileName: String, range: TextRange, message: String): Unit = {
    val escaped = escapeTC(message)
    val testName = s"$name.$fileName${range.toString}"
    tcPrint(s"testStarted name='$testName'")
    tcPrint(s"testFailed name='$testName' message='Highlighting error' details='$escaped'")
    tcPrint(s"testFinished name='$testName'")
  }

  override def reportResults(): Unit = {
    val totalErrors = unexpectedErrors.size
    val fixedFiles = unexpectedSuccess

    if (totalErrors > 0) {
      tcPrint(s"buildProblem description='Found $totalErrors errors while highlighting the project'")
    }
    else if (fixedFiles.nonEmpty) {
      val filesString = fixedFiles.mkString(", ")
      tcPrint(s"buildProblem description='Files $filesString successfully highlighted, fix test definition'")
    }
    else if (reportSuccess) {
      tcPrint("buildStatus status='SUCCESS' text='No highlighting errors found in project'")
    }
  }

  override val progressIndicator: ProgressIndicator = new TextBasedProgressIndicator {
    override def setText(text: String): Unit = {
      if (getText != text) {
        super.setText(text)
        tcPrint(s"progressMessage '$text'")
      }
    }

    override def setText2(text: String): Unit = setText(text)
  }

  override def notify(message: String): Unit = progressMessage(message)
}

object TeamCityReporter {
  private def escapeTC(message: String): String = {
    message
      .replaceAll("##", "")
      .replaceAll("([\"\'\n\r\\|\\[\\]])", "\\|$1")
  }

  private def tcPrint(message: String): Unit = println(s"##teamcity[$message]")
  private def progressMessage(content: String): Unit = tcPrint(s"progressMessage '$content'")
}
