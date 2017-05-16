package org.jetbrains.plugins.scala.util.reporter

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile

/**
  * @author mutcianm
  * @since 16.05.17.
  */
class TeamCityReporter extends ConsoleReporter {
  import TeamCityReporter._

  override def updateHighlightingProgress(percent: Int): Unit = progressMessage(s"Highlighting - $percent%")

  override def reportError(file: VirtualFile, range: TextRange, message: String): Unit = {
    totalErrors += 1
    val escaped = escapeTC(Option(message).getOrElse(""))
    val testName = s"${getClass.getName}.${Option(file).map(_.getName).getOrElse("UNKNOWN")}${Option(range).map(_.toString).getOrElse("(UNKNOWN)")}"
    tcPrint(s"testStarted name='$testName'")
    tcPrint(s"testFailed name='$testName' message='Highlighting error' details='$escaped'")
    tcPrint(s"testFinished name='$testName'")
  }

  override def reportResults(): Unit = {
    if (totalErrors > 0)
      tcPrint(s"buildProblem description='Found $totalErrors errors while highlighting the project'")
    else
      tcPrint("buildStatus status='SUCCESS' text='No highlighting errors found in project'")
  }

  override val progressIndicator: ProgressIndicator = new ProgressIndicatorBase() {
    override def setText(text: String): Unit = {
      if (getText != text) {
        super.setText(text)
        tcPrint(s"progressMessage '$text'")
      }
    }
  }

  override def notify(message: String) = progressMessage(message)
}

object TeamCityReporter {
  private def escapeTC(message: String): String = {
    message
      .replaceAll("##", "")
      .replaceAll("([\"\'\n\r\\|\\[\\]])", "\\|$1")
  }

  private def tcPrint(message: String) = println(s"##teamcity[$message]")
  private def progressMessage(content: String) = tcPrint(s"progressMessage '$content'")
}
