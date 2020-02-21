package org.jetbrains.plugins.scala
package annotator
package annotationHolder

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.ASTNode
import com.intellij.lang.annotation.{Annotation, AnnotationHolder, AnnotationSession, HighlightSeverity}
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

import scala.language.implicitConversions

class ScalaAnnotationHolderAdapter(innerHolder: AnnotationHolder) extends ScalaAnnotationHolder {

  private val showCompilerErrors =
    ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(innerHolder.getCurrentAnnotationSession.getFile)

  override def createErrorAnnotation(elt: PsiElement, message: String): ScalaAnnotation =
    innerHolder.createErrorAnnotation(elt, ?(message))

  override def createErrorAnnotation(node: ASTNode, message: String): ScalaAnnotation =
    innerHolder.createErrorAnnotation(node, ?(message))

  override def createErrorAnnotation(range: TextRange, message: String): ScalaAnnotation =
    innerHolder.createErrorAnnotation(range, ?(message))

  override def createWarningAnnotation(elt: PsiElement, message: String): ScalaAnnotation =
    innerHolder.createWarningAnnotation(elt, ?(message))

  override def createWarningAnnotation(node: ASTNode, message: String): ScalaAnnotation =
    innerHolder.createWarningAnnotation(node, ?(message))

  override def createWarningAnnotation(range: TextRange, message: String): ScalaAnnotation =
    innerHolder.createWarningAnnotation(range, ?(message))

  override def createWeakWarningAnnotation(elt: PsiElement, message: String): ScalaAnnotation =
    innerHolder.createWeakWarningAnnotation(elt, ?(message))

  override def createWeakWarningAnnotation(node: ASTNode, message: String): ScalaAnnotation =
    innerHolder.createWeakWarningAnnotation(node, ?(message))

  override def createWeakWarningAnnotation(range: TextRange, message: String): ScalaAnnotation =
    innerHolder.createWeakWarningAnnotation(range, ?(message))

  override def createInfoAnnotation(elt: PsiElement, message: String): ScalaAnnotation =
    innerHolder.createInfoAnnotation(elt, ?(message))

  override def createInfoAnnotation(node: ASTNode, message: String): ScalaAnnotation =
    innerHolder.createInfoAnnotation(node, ?(message))

  override def createInfoAnnotation(range: TextRange, message: String): ScalaAnnotation =
    innerHolder.createInfoAnnotation(range, ?(message))

  override def createAnnotation(severity: HighlightSeverity, range: TextRange, message: String): ScalaAnnotation =
    innerHolder.createAnnotation(severity, range, ?(message))

  override def createAnnotation(severity: HighlightSeverity, range: TextRange, message: String, htmlTooltip: String): ScalaAnnotation =
    innerHolder.createAnnotation(severity, range, ?(message), ?(htmlTooltip))

  override def getCurrentAnnotationSession: AnnotationSession =
    innerHolder.getCurrentAnnotationSession

  override def isBatchMode: Boolean =
    innerHolder.isBatchMode

  private implicit def annotation2ScalaAnnotation(annotation: Annotation): ScalaAnnotation =
    if (showCompilerErrors) {
      annotation.setHighlightType(ProblemHighlightType.INFORMATION)
      new ScalaAnnotation(annotation) {
        override def setHighlightType(highlightType: ProblemHighlightType): Unit = ()
        override def setTooltip(tooltip: String): Unit = ()
      }
    } else {
      new ScalaAnnotation(annotation)
    }

  private def ?(msg: String): String =
    Option(msg)
      .filterNot(_ => showCompilerErrors)
      .orNull
}
