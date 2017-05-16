package org.jetbrains.plugins.scala.util.reporter

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.{CommandLineProgress, ProgressIndicatorBase}
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.scala.util.reporter.TeamCityReporter.tcPrint
import org.junit.Assert

/**
  * @author mutcianm
  * @since 16.05.17.
  */
class ConsoleReporter extends ProgressReporter {

  class TextBasedProgressIndicator extends ProgressIndicatorBase(false) {
    private var oldPercent = -1
    override def setText2(text: String) = setText(text)

    override def setText(text: String) = {
      if (getText != text) {
        super.setText(text)
        println(text)
      }
    }

    override def setFraction(fraction: Double) = {
      super.setFraction(fraction)
      val percent = (fraction * 100).toInt
      val rounded = (percent / 10) * 10
      if (rounded != oldPercent) {
        oldPercent = rounded
        setText(s"Downloading project - $rounded%")
      }
    }
  }

  protected var totalErrors = 0

  def reportError(file: VirtualFile, range: TextRange, message: String) = {
    totalErrors += 1
    println(s"Error: ${file.getName}${range.toString} - $message")
  }

  def updateHighlightingProgress(percent: Int) = {
    println(s"Highlighting -  $percent%")
  }

  def reportResults() = {
    Assert.assertTrue(s"Found $totalErrors errors while highlighting the project", totalErrors == 0)
  }

  override val progressIndicator: ProgressIndicator = new TextBasedProgressIndicator
  override def notify(message: String) = println(message)
}
