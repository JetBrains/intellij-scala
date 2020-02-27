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
    convert1(elt, message)(innerHolder.createErrorAnnotation)

  override def createErrorAnnotation(node: ASTNode, message: String): ScalaAnnotation =
    convert2(node, message)(innerHolder.createErrorAnnotation)

  override def createErrorAnnotation(range: TextRange, message: String): ScalaAnnotation =
    convert3(range, message)(innerHolder.createErrorAnnotation)

  override def createWarningAnnotation(elt: PsiElement, message: String): ScalaAnnotation =
    convert1(elt, message)(innerHolder.createWarningAnnotation)

  override def createWarningAnnotation(node: ASTNode, message: String): ScalaAnnotation =
    convert2(node, message)(innerHolder.createWarningAnnotation)

  override def createWarningAnnotation(range: TextRange, message: String): ScalaAnnotation =
    convert3(range, message)(innerHolder.createWarningAnnotation)

  override def createWeakWarningAnnotation(elt: PsiElement, message: String): ScalaAnnotation =
    convert1(elt, message)(innerHolder.createWeakWarningAnnotation)

  override def createWeakWarningAnnotation(node: ASTNode, message: String): ScalaAnnotation =
    convert2(node, message)(innerHolder.createWeakWarningAnnotation)

  override def createWeakWarningAnnotation(range: TextRange, message: String): ScalaAnnotation =
    convert3(range, message)(innerHolder.createWeakWarningAnnotation)

  override def createInfoAnnotation(elt: PsiElement, message: String): ScalaAnnotation =
    convert1(elt, message)(innerHolder.createInfoAnnotation)

  override def createInfoAnnotation(node: ASTNode, message: String): ScalaAnnotation =
    convert2(node, message)(innerHolder.createInfoAnnotation)

  override def createInfoAnnotation(range: TextRange, message: String): ScalaAnnotation =
    convert3(range, message)(innerHolder.createInfoAnnotation)

  override def createAnnotation(severity: HighlightSeverity, range: TextRange, message: String): ScalaAnnotation =
    if (showCompilerErrors)
      innerHolder.createAnnotation(HighlightSeverity.INFORMATION, range, convertMsg(message))
    else
      innerHolder.createAnnotation(severity, range, message)

  override def createAnnotation(severity: HighlightSeverity, range: TextRange, message: String, htmlTooltip: String): ScalaAnnotation =
    if (showCompilerErrors)
      innerHolder.createAnnotation(HighlightSeverity.INFORMATION, range, convertMsg(message), convertMsg(htmlTooltip))
    else
      innerHolder.createAnnotation(severity, range, message, htmlTooltip)

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

  private def convert1(elt: PsiElement, message: String)
                      (f: (PsiElement, String) => Annotation): Annotation =
    if (showCompilerErrors)
      innerHolder.createInfoAnnotation(elt, convertMsg(message))
    else
      f(elt, message)

  private def convert2(node: ASTNode, message: String)
                      (f: (ASTNode, String) => Annotation): Annotation =
    if (showCompilerErrors)
      innerHolder.createInfoAnnotation(node, convertMsg(message))
    else
      f(node, message)

  private def convert3(range: TextRange, message: String)
                      (f: (TextRange, String) => Annotation): Annotation =
    if (showCompilerErrors)
      innerHolder.createInfoAnnotation(range, convertMsg(message))
    else
      f(range, message)

  private def convertMsg(message: String): String =
    Option(message).map(_ => "").orNull
}
