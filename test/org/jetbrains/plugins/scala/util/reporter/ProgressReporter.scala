package org.jetbrains.plugins.scala.util.reporter

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile

/**
  * @author mutcianm
  * @since 16.05.17.
  */
trait ProgressReporter {
  def reportError(file: VirtualFile, range: TextRange, message: String)
  def notify(message: String)
  def updateHighlightingProgress(percent: Int)
  def reportResults()
  def progressIndicator: ProgressIndicator
}

object ProgressReporter {
  def getInstance: ProgressReporter = {
    if (sys.env.contains("TEAMCITY_VERSION")) new TeamCityReporter else new ConsoleReporter
  }
}
