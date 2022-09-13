package org.jetbrains.plugins.scala.settings

import com.intellij.codeInsight.daemon.impl.analysis.{FileHighlightingSetting, HighlightingSettingsPerFile}
import com.intellij.openapi.application.ApplicationManager
//noinspection ApiStatus
import com.intellij.ide.impl.TrustedProjects
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.{PsiFile, PsiJavaFile}
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.extensions.PsiFileExt
import org.jetbrains.plugins.scala.lang.psi.api.{ScFile, ScalaFile}
import org.jetbrains.plugins.scala.project.{ProjectExt, ProjectPsiElementExt}
import org.jetbrains.plugins.scala.settings.{CompilerHighlightingListener, ScalaProjectSettings}

import scala.concurrent.duration._

object ScalaHighlightingMode {

  @TestOnly
  private[scala] var compilerHighlightingEnabledInTests: Boolean = false

  private def isInTestMode: Boolean = ApplicationManager.getApplication.isUnitTestMode

  private def showCompilerErrorsScala2(project: Project): Boolean =
    compilerHighlightingEnabledInTests ||
      !isInTestMode && ScalaProjectSettings.getInstance(project).isCompilerHighlightingScala2

  def showCompilerErrorsScala3(project: Project): Boolean =
    compilerHighlightingEnabledInTests ||
      !isInTestMode && ScalaProjectSettings.getInstance(project).isCompilerHighlightingScala3

  def addSettingsListener(project: Project)
                         (listener: CompilerHighlightingListener): Unit =
    project.getMessageBus
      .connect(project.unloadAwareDisposable)
      .subscribe(CompilerHighlightingListener.Topic, listener)


  /**
   * Should only be used in situations where there is no reference to a [[com.intellij.psi.PsiFile]] instance. The other
   * method that takes in a PsiFile instance as an argument should be preferred, as it does the same checks, but also
   * takes into consideration the Scala language version.
   */
  //noinspection ApiStatus,UnstableApiUsage
  def isShowErrorsFromCompilerEnabled(project: Project): Boolean =
    TrustedProjects.isTrusted(project) &&
      (showCompilerErrorsScala3(project) && project.hasScala3 ||
        showCompilerErrorsScala2(project) && project.hasScala)

  def isShowErrorsFromCompilerEnabled(file: PsiFile): Boolean =
    file match {
      case scalaFile: ScalaFile => isShowErrorsFromCompilerEnabled(scalaFile)
      case javaFile: PsiJavaFile => isShowErrorsFromCompilerEnabled(javaFile)
      case _ => false
    }

  private[scala] def shouldHighlightBasedOnFileLevel(psiFile: PsiFile, project: Project): Boolean = {
    val level = HighlightingSettingsPerFile.getInstance(project).getHighlightingSettingForRoot(psiFile)
    import FileHighlightingSetting.{ESSENTIAL, FORCE_HIGHLIGHTING}
    level match {
      case ESSENTIAL | FORCE_HIGHLIGHTING => true
      case _ => false
    }
  }

  private def isShowErrorsFromCompilerEnabled(file: ScalaFile): Boolean = {
    val virtualFile = file match {
      case ScFile.VirtualFile(vFile) => vFile
      case _                         => return false
    }

    val project = file.getProject
    val isRegularScalaFile = virtualFile.getFileType == ScalaFileType.INSTANCE
    if (isRegularScalaFile) {
      file.isScala3File && showCompilerErrorsScala3(project) ||
        file.isScala2File && showCompilerErrorsScala2(project)
    } else if (file.isWorksheetFile) {
      // actually this should work for regular files as well
      file.isInScala3Module && showCompilerErrorsScala3(project) ||
        file.isInScalaModule && showCompilerErrorsScala2(project)
    } else {
      false
    }
  }

  private def isShowErrorsFromCompilerEnabled(javaFile: PsiJavaFile): Boolean =
    showCompilerErrorsScala3(javaFile.getProject) && javaFile.isInScala3Module

  private def nonNegativeDuration(key: String): FiniteDuration =
    math.max(Registry.intValue(key), 0).millis

  def compilationDelay: FiniteDuration =
    nonNegativeDuration("scala.highlighting.compilation.delay.millis")

  def compilationTimeoutToShowProgress: FiniteDuration =
    nonNegativeDuration("scala.highlighting.compilation.timeout.to.show.progress.millis")
}
