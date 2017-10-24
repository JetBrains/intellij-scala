package org.jetbrains.plugins.scala.util.reporter

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.TextRange

/**
  * @author mutcianm
  * @since 16.05.17.
  */
trait ProgressReporter {
  def reportError(fileName: String, range: TextRange, message: String)
  def notify(message: String)
  def updateHighlightingProgress(percent: Int)
  def reportResults()
  def progressIndicator: ProgressIndicator
}

object ProgressReporter {
  def newInstance(name: String, reportSuccess: Boolean = true): ProgressReporter = {
    if (sys.env.contains("TEAMCITY_VERSION")) new TeamCityReporter(name, reportSuccess) else new ConsoleReporter
  }
}
