package org.jetbrains.plugins.scala.annotator

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.extensions.PsiFileExt
import org.jetbrains.plugins.scala.project.{ModuleExt, ProjectExt, ProjectPsiElementExt}
import org.jetbrains.plugins.scala.{Scala3Language, ScalaFileType, isUnitTestMode}

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

  private def showDotcErrors: Boolean = Registry.is("dotty.show.compiler.errors.in.editor")

  private def showScalacErrors: Boolean = Registry.is("scala.show.compiler.errors.in.editor")

  private def hasDotty(project: Project) = project.modulesWithScala.exists(_.hasScala3)

  private def isScala3File(file: PsiFile) = file.getLanguage == Scala3Language.INSTANCE
}
