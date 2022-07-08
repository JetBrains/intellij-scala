package org.jetbrains.plugins.scala.util.reporter

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.util.reporter.ProgressReporter.TextBasedProgressIndicator

class TeamCityReporter(name: String, override val filesWithProblems: Map[String, Set[TextRange]], reportStatus: Boolean) extends ProgressReporter {
  import TeamCityReporter._

  override def updateHighlightingProgress(percent: Int, fileName: String): Unit =
    progressMessage(s"$percent% highlighted, started $fileName")

  override def showError(fileError: FileErrorDescriptor): Unit = {
    val error = fileError.error

    val escaped = escapeTC(error.message)
    val testName = s"$name.${fileError.fileName}${error.range.toString}"
    tcPrint(s"testStarted name='$testName'")
    tcPrint(s"testFailed name='$testName' message='Highlighting error' details='$escaped'")
    tcPrint(s"testFinished name='$testName'")
  }

  override def reportResults(): Unit = {
    val totalErrors = unexpectedErrors.size
    val fixedFiles = unexpectedSuccess

    if (totalErrors > 0) {
      testsWithProblems += name
      tcPrint(s"buildProblem description='Found $totalErrors errors in $name'")
    }

    if (fixedFiles.nonEmpty) {
      testsWithProblems += name
      val filesString = fixedFiles.mkString(", ")
      tcPrint(s"buildProblem description='Files $filesString successfully highlighted in $name, fix test definition'")
    }

    if (reportStatus) {
      if (testsWithProblems.isEmpty) {
        // NOTE: do not set build status, otherwise, even if there were failed tests before current test class,
        //  that "FAILURE" status will be overwritten by this 'SUCCESS' status.
        //  It results into "success" builds with failed tests.
        // Anyway, not clear why we previously set the status here?
        //tcPrint("buildStatus status='SUCCESS' text='No highlighting errors found in project'")
      } else {
        val testNames = testsWithProblems.mkString(", ")
        tcPrint(s"buildStatus status='FAILURE' text='Problems found in $testNames'")
      }
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
  private var testsWithProblems: Set[String] = Set.empty

  private def escapeTC(message: String): String = {
    message
      .replaceAll("##", "")
      .replaceAll("([\"\'\n\r\\|\\[\\]])", "\\|$1")
  }

  private def tcPrint(message: String): Unit = println(s"##teamcity[$message]")
  private def progressMessage(content: String): Unit = tcPrint(s"progressMessage '$content'")
}
