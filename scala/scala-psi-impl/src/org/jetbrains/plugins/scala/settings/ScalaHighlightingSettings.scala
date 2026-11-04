package org.jetbrains.plugins.scala.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.annotations.{Nullable, TestOnly}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.project.{ProjectExt, ProjectPsiElementExt}

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

  /**
   * Whether the Scala 3 compiler reports the errors of the element's file, so the checks it covers can be skipped.
   * False for files left to the annotator, see [[isLeftToAnnotator]].
   */
  def showCompilerErrorsScala3(element: PsiElement): Boolean =
    showCompilerErrorsScala3(element.getProject) && element.isInScala3Module &&
    !Option(element.getContainingFile).exists(isLeftToAnnotator)

  def isBuiltInHighlightingDisabled(project: Project): Boolean = {
    val settings = ScalaProjectSettings.in(project)
    def isCompilerHighlightingOnly = (settings.isCompilerHighlightingScala2 || !project.hasScala2) && (settings.isCompilerHighlightingScala3 || !project.hasScala3)
    settings.isDisableInspections && isCompilerHighlightingOnly
  }

  /** Incremental highlighting (SCL-23216) is off until the current highlighting pass finishes */
  private[scala] var incrementalHighlightingSuppressed: Boolean = false

  def isIncrementalHighlightingEnabled(@Nullable project: Project): Boolean =
    !incrementalHighlightingSuppressed && project != null && ScalaProjectSettings.in(project).isIncrementalHighlighting

  /**
   * Mill build files are left to the annotator, like sbt build files: Mill rewrites them before compiling them, so they
   * cannot be compiled as written, and its build server never clears the errors it reports on them.
   */
  private[settings] def isLeftToAnnotator(file: PsiFile): Boolean = file match {
    case scalaFile: ScalaFile => scalaFile.isMillFile
    case _                    => false
  }
}
