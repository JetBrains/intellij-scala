package org.jetbrains.plugins.scala.annotator

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.extensions.PsiFileExt
import org.jetbrains.plugins.scala.lang.psi.api.{ScFile, ScalaFile}
import org.jetbrains.plugins.scala.project.ProjectExt

import scala.concurrent.duration._

object ScalaHighlightingMode {

  final val ShowDotcErrorsKey = "dotty.highlighting.compiler.errors.in.editor"
  final val ShowScalacErrorsKey = "scala.highlighting.compiler.errors.in.editor"

  private def showDotcErrors: Boolean = Registry.is(ShowDotcErrorsKey)
  private def showScalacErrors: Boolean = Registry.is(ShowScalacErrorsKey)

  def isShowErrorsFromCompilerEnabled(project: Project): Boolean =
    showDotcErrors && project.hasScala3 || showScalacErrors && project.hasScala

  def isShowErrorsFromCompilerEnabled(file: PsiFile): Boolean =
    file match {
      case scalaFile: ScalaFile =>
        isShowErrorsFromCompilerEnabled(scalaFile)
      case _ => false
    }

  def isShowErrorsFromCompilerEnabled(file: ScalaFile): Boolean = {
    val virtualFile = file match {
      case ScFile.VirtualFile(vFile) => vFile
      case _                         => return false
    }

    val isRegularScalaFile = virtualFile.getFileType == ScalaFileType.INSTANCE
    if (isRegularScalaFile) {
      file.isScala3File && showDotcErrors || file.isScala2File && showScalacErrors
    } else if (file.isWorksheetFile) {
      // actually this should work for regular files as well
      file.isInScala3Module && showDotcErrors || file.isInScalaModule && showScalacErrors
    } else {
      false
    }
  }

  // do we need this method now? we have a flag for scala 2 so `isShowErrorsFromCompilerEnabled` should be enough
  def showParserErrors(file: PsiFile): Boolean = {
    val shouldSkip = file.isScala3File && isShowErrorsFromCompilerEnabled(file)

    !shouldSkip
  }
  
  private def nonNegativeDuration(key: String): FiniteDuration =
    Seq(Registry.get(key).asInteger, 0).max.millis

  def compilationDelay: FiniteDuration =
    nonNegativeDuration("scala.highlighting.compilation.delay.millis")
  
  def compilationTimeoutToShowProgress: FiniteDuration =
    nonNegativeDuration("scala.highlighting.compilation.timeout.to.show.progress.millis")
}
