package org.jetbrains.plugins.scala.util.reporter

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.CommandLineProgress
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import org.junit.Assert

/**
  * @author mutcianm
  * @since 16.05.17.
  */
class ConsoleReporter extends ProgressReporter {
  var totalErrors = 0

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

  override def progressIndicator: ProgressIndicator = new CommandLineProgress

  override def notify(message: String) = println(message)
}
