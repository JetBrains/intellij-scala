package org.jetbrains.plugins.scala.annotator

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.extensions.PsiFileExt
import org.jetbrains.plugins.scala.project.{ModuleExt, ProjectExt}
import org.jetbrains.plugins.scala.{Scala3Language, ScalaFileType, isUnitTestMode}

import scala.concurrent.duration._

object ScalaHighlightingMode {
  def isShowErrorsFromCompilerEnabled(project: Project): Boolean =
    showDotcErrors && hasDotty(project) || showScalacErrors && project.hasScala

  def isShowErrorsFromCompilerEnabled(file: PsiFile): Boolean = {
    val isRegularScalaFile = file.getVirtualFile.getExtension == ScalaFileType.INSTANCE.getDefaultExtension
    val enabled = isScala3File(file) && showDotcErrors || showScalacErrors

    isRegularScalaFile && enabled
  }

  def isScalaAnnotatorEnabled(file: PsiFile): Boolean = {
    val isScalaFile = file.hasScalaPsi

    isScalaFile && (!isShowErrorsFromCompilerEnabled(file) || isUnitTestMode)
  }

  def showParserErrors(file: PsiFile): Boolean = {
    val shouldSkip = isScala3File(file) && isShowErrorsFromCompilerEnabled(file)

    !shouldSkip
  }

  def compilationDelay: FiniteDuration =
    nonNegativeDuration("scala.highlighting.compiler.selected.delay.millis")

  def compilationJpsDelay: FiniteDuration =
    nonNegativeDuration("scala.highlighting.compiler.jps.delay.millis")

  private def nonNegativeDuration(key: String): FiniteDuration =
    Seq(Registry.get(key).asInteger, 0).max.millis

  private def showDotcErrors: Boolean =
    Registry.is("dotty.highlighting.compiler.errors.in.editor")

  private def showScalacErrors: Boolean =
    Registry.is("scala.highlighting.compiler.errors.in.editor")

  private def hasDotty(project: Project) =
    project.modulesWithScala.exists(_.hasScala3)

  private def isScala3File(file: PsiFile) =
    file.getLanguage == Scala3Language.INSTANCE
}
