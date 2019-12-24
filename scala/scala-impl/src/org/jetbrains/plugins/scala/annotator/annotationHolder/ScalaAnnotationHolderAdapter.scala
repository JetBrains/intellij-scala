package org.jetbrains.plugins.scala
package annotator
package annotationHolder

import com.intellij.lang.ASTNode
import com.intellij.lang.annotation.{Annotation, AnnotationHolder, AnnotationSession, HighlightSeverity}
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

class ScalaAnnotationHolderAdapter(private val innerHolder: AnnotationHolder) extends ScalaAnnotationHolder {

  override def createErrorAnnotation(elt: PsiElement, message: String): Annotation =
    innerHolder.createErrorAnnotation(elt, message)

  override def createErrorAnnotation(node: ASTNode, message: String): Annotation =
    innerHolder.createErrorAnnotation(node, message)

  override def createErrorAnnotation(range: TextRange, message: String): Annotation =
    innerHolder.createErrorAnnotation(range, message)

  override def createWarningAnnotation(elt: PsiElement, message: String): Annotation =
    innerHolder.createWarningAnnotation(elt, message)

  override def createWarningAnnotation(node: ASTNode, message: String): Annotation =
    innerHolder.createWarningAnnotation(node, message)

  override def createWarningAnnotation(range: TextRange, message: String): Annotation =
    innerHolder.createWarningAnnotation(range, message)

  override def createWeakWarningAnnotation(elt: PsiElement, message: String): Annotation =
    innerHolder.createWeakWarningAnnotation(elt, message)

  override def createWeakWarningAnnotation(node: ASTNode, message: String): Annotation =
    innerHolder.createWeakWarningAnnotation(node, message)

  override def createWeakWarningAnnotation(range: TextRange, message: String): Annotation =
    innerHolder.createWeakWarningAnnotation(range, message)

  override def createInfoAnnotation(elt: PsiElement, message: String): Annotation =
    innerHolder.createInfoAnnotation(elt, message)

  override def createInfoAnnotation(node: ASTNode, message: String): Annotation =
    innerHolder.createInfoAnnotation(node, message)

  override def createInfoAnnotation(range: TextRange, message: String): Annotation =
    innerHolder.createInfoAnnotation(range, message)

  override def createAnnotation(severity: HighlightSeverity, range: TextRange, message: String): Annotation =
    innerHolder.createAnnotation(severity, range, message)

  override def createAnnotation(severity: HighlightSeverity, range: TextRange, message: String, htmlTooltip: String): Annotation =
    innerHolder.createAnnotation(severity, range, message, htmlTooltip)

  override def getCurrentAnnotationSession: AnnotationSession =
    innerHolder.getCurrentAnnotationSession

  override def isBatchMode: Boolean =
    innerHolder.isBatchMode
}
