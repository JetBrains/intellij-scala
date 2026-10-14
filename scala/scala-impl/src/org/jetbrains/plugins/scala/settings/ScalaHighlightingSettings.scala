package org.jetbrains.plugins.scala.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.{Nullable, TestOnly}
import org.jetbrains.plugins.scala.project.ProjectExt

/**
 * The highlighting mode as configured in [[ScalaProjectSettings]], without the IDE-level checks (trusted project,
 * per-file highlighting level), so the PSI can use it.
 *
 * @see [[ScalaHighlightingMode]]
 */
object ScalaHighlightingSettings {

  @TestOnly
  private[scala] var compilerHighlightingEnabledInTests: Boolean = false

  private def isInTestMode: Boolean = ApplicationManager.getApplication.isUnitTestMode

  def showCompilerErrorsScala2(project: Project): Boolean =
    compilerHighlightingEnabledInTests ||
      !isInTestMode && ScalaProjectSettings.getInstance(project).isCompilerHighlightingScala2

  def showCompilerErrorsScala3(project: Project): Boolean =
    compilerHighlightingEnabledInTests ||
      !isInTestMode && ScalaProjectSettings.getInstance(project).isCompilerHighlightingScala3

  def isBuiltInHighlightingDisabled(project: Project): Boolean = {
    val settings = ScalaProjectSettings.in(project)
    def isCompilerHighlightingOnly = (settings.isCompilerHighlightingScala2 || !project.hasScala2) && (settings.isCompilerHighlightingScala3 || !project.hasScala3)
    settings.isDisableInspections && isCompilerHighlightingOnly
  }

  /** Incremental highlighting (SCL-23216) is off until the current highlighting pass finishes */
  private[scala] var incrementalHighlightingSuppressed: Boolean = false

  def isIncrementalHighlightingEnabled(@Nullable project: Project): Boolean =
    !incrementalHighlightingSuppressed && project != null && ScalaProjectSettings.in(project).isIncrementalHighlighting
}
