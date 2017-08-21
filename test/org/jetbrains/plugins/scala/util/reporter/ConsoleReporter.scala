package org.jetbrains.plugins.scala.util.reporter

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.util.TextRange
import org.junit.Assert

import scala.collection.mutable.ArrayBuffer

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

  protected val errorMessages = ArrayBuffer[String]()

  def reportError(fileName: String, range: TextRange, message: String) = {
    val errMessage = s"Error: $fileName${range.toString} - $message"
    errorMessages += errMessage
    System.err.println(errMessage)
  }

  def updateHighlightingProgress(percent: Int) = {
    println(s"Highlighting -  $percent%")
  }

  def reportResults() = {
    val totalErrors = errorMessages.size
    val allMessages = errorMessages.mkString(s"Found $totalErrors errors\n\n", "\n", "")
    Assert.assertTrue(allMessages, totalErrors == 0)
  }

  override val progressIndicator: ProgressIndicator = new TextBasedProgressIndicator
  override def notify(message: String) = println(message)
}
