package org.jetbrains.plugins.scala.externalHighlighters

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.{Registry, RegistryValueListener}
import com.intellij.psi.{PsiFile, PsiJavaFile}
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.lang.psi.api.{ScFile, ScalaFile}
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.extensions.PsiFileExt
import org.jetbrains.plugins.scala.project.ProjectPsiElementExt
import org.jetbrains.plugins.scala.settings.{CompilerHighlightingListener, ScalaProjectSettings}

import scala.concurrent.duration._

object ScalaHighlightingMode {

  private def showScala2Errors(project: Project): Boolean =
    ScalaProjectSettings.getInstance(project).isCompilerHighlightingScala2
  def showScala3Errors(project: Project): Boolean =
    ScalaProjectSettings.getInstance(project).isCompilerHighlightingScala3

  def addSettingsListener(project: Project)
                         (listener: CompilerHighlightingListener): Unit =
    project.getMessageBus
      .connect(project.unloadAwareDisposable)
      .subscribe(CompilerHighlightingListener.Topic, listener)

  def isShowErrorsFromCompilerEnabled(project: Project): Boolean =
    showScala3Errors(project) && project.hasScala3 ||
      showScala2Errors(project) && project.hasScala

  def isShowErrorsFromCompilerEnabled(file: PsiFile): Boolean =
    file match {
      case scalaFile: ScalaFile => isShowErrorsFromCompilerEnabled(scalaFile)
      case javaFile: PsiJavaFile => isShowErrorsFromCompilerEnabled(javaFile)
      case _ => false
    }

  private def isShowErrorsFromCompilerEnabled(file: ScalaFile): Boolean = {
    val virtualFile = file match {
      case ScFile.VirtualFile(vFile) => vFile
      case _                         => return false
    }

    val project = file.getProject
    val isRegularScalaFile = virtualFile.getFileType == ScalaFileType.INSTANCE
    if (isRegularScalaFile) {
      file.isScala3File && showScala3Errors(project) ||
        file.isScala2File && showScala2Errors(project)
    } else if (file.isWorksheetFile) {
      // actually this should work for regular files as well
      file.isInScala3Module && showScala3Errors(project) ||
        file.isInScalaModule && showScala2Errors(project)
    } else {
      false
    }
  }

  private def isShowErrorsFromCompilerEnabled(javaFile: PsiJavaFile): Boolean =
    showScala3Errors(javaFile.getProject) && javaFile.isInScala3Module

  def showParserErrors(file: PsiFile): Boolean = {
    val shouldSkip = file.isInScala3Module && isShowErrorsFromCompilerEnabled(file)
    !shouldSkip
  }
  
  private def nonNegativeDuration(key: String): FiniteDuration =
    Seq(Registry.get(key).asInteger, 0).max.millis

  def compilationDelay: FiniteDuration =
    nonNegativeDuration("scala.highlighting.compilation.delay.millis")
  
  def compilationTimeoutToShowProgress: FiniteDuration =
    nonNegativeDuration("scala.highlighting.compilation.timeout.to.show.progress.millis")
}
