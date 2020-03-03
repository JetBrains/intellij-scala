package org.jetbrains.plugins.scala.annotator

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.project.ProjectExt
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.extensions.PsiFileExt

import scala.concurrent.duration._

object ScalaHighlightingMode {
  def isShowErrorsFromCompilerEnabled(project: Project): Boolean =
    showDotcErrors && project.hasDotty || showScalacErrors && project.hasScala

  def isShowErrorsFromCompilerEnabled(file: PsiFile): Boolean = {
    val isRegularScalaFile = Option(file.getVirtualFile)
      .exists(_.getExtension == ScalaFileType.INSTANCE.getDefaultExtension)
    val enabled = file.isScala3File && showDotcErrors || showScalacErrors

    isRegularScalaFile && enabled
  }

  def showParserErrors(file: PsiFile): Boolean = {
    val shouldSkip = file.isScala3File && isShowErrorsFromCompilerEnabled(file)

    !shouldSkip
  }

  def compilationDelay: FiniteDuration =
    nonNegativeDuration("scala.highlighting.compiler.selected.delay.millis")

  def compilationJpsDelay: FiniteDuration =
    nonNegativeDuration("scala.highlighting.compiler.jps.delay.millis")

  private def nonNegativeDuration(key: String): FiniteDuration =
    Seq(Registry.get(key).asInteger, 0).max.millis

  final val ShowDotcErrorsKey = "dotty.highlighting.compiler.errors.in.editor"

  private def showDotcErrors: Boolean =
    Registry.is(ShowDotcErrorsKey)

  final val ShowScalacErrorsKey = "scala.highlighting.compiler.errors.in.editor"

  private def showScalacErrors: Boolean =
    Registry.is(ShowScalacErrorsKey)
}
