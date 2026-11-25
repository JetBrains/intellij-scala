package org.jetbrains.plugins.scala.settings

import com.intellij.codeInsight.daemon.impl.analysis.{FileHighlightingSetting, HighlightingSettingsPerFile}
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.scala.project.ProjectExt

import scala.concurrent.duration._

object ScalaHighlightingMode {

  @TestOnly
  private[scala] def compilerHighlightingEnabledInTests: Boolean = ScalaHighlightingSettings.compilerHighlightingEnabledInTests

  @TestOnly
  private[scala] def compilerHighlightingEnabledInTests_=(value: Boolean): Unit =
    ScalaHighlightingSettings.compilerHighlightingEnabledInTests = value

  private def showCompilerErrorsScala2(project: Project): Boolean = ScalaHighlightingSettings.showCompilerErrorsScala2(project)

  def showCompilerErrorsScala3(project: Project): Boolean = ScalaHighlightingSettings.showCompilerErrorsScala3(project)

  /** @see [[ScalaHighlightingSettings.showCompilerErrorsScala3]] */
  def showCompilerErrorsScala3(element: PsiElement): Boolean =
    ScalaHighlightingSettings.showCompilerErrorsScala3(element)

  /**
   * Should only be used in situations where there is no reference to a [[com.intellij.psi.PsiFile]] instance. The other
   * method that takes in a PsiFile instance as an argument should be preferred, as it does the same checks, but also
   * takes into consideration the Scala language version.
   */
  //noinspection ApiStatus,UnstableApiUsage
  def isShowErrorsFromCompilerEnabled(project: Project): Boolean =
    TrustedProjects.isProjectTrusted(project) &&
      (showCompilerErrorsScala3(project) && project.hasScala3 ||
        showCompilerErrorsScala2(project) && project.hasScala)

  def isShowErrorsFromCompilerEnabled(file: PsiFile): Boolean =
    ScalaHighlightingSettings.isShowErrorsFromCompilerEnabled(file)

  private[scala] def shouldHighlightBasedOnFileLevel(psiFile: PsiFile, project: Project): Boolean = {
    val level = HighlightingSettingsPerFile.getInstance(project).getHighlightingSettingForRoot(psiFile)
    import FileHighlightingSetting.{ESSENTIAL, FORCE_HIGHLIGHTING}
    level match {
      case ESSENTIAL | FORCE_HIGHLIGHTING => true
      case _ => false
    }
  }

  private def nonNegativeDuration(key: String): FiniteDuration =
    math.max(Registry.intValue(key), 0).millis

  def compilationDelay(project: Project): FiniteDuration =
    ScalaProjectSettings.in(project).getCompilerHighlightingDelay.millis

  def compilationTimeoutToShowProgress: FiniteDuration =
    nonNegativeDuration("scala.highlighting.compilation.timeout.to.show.progress.millis")

  def useCompilerRanges: Boolean = Registry.is("scala.compiler.highlighting.use.compiler.ranges")
}
