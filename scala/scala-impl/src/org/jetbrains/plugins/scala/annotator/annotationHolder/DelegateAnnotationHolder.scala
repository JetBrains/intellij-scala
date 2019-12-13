package org.jetbrains.plugins.scala
package annotator
package annotationHolder

import com.intellij.lang.ASTNode
import com.intellij.lang.annotation.{Annotation, AnnotationSession, HighlightSeverity}
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

abstract class DelegateAnnotationHolder(session: AnnotationSession)
                                       (implicit holder: ScalaAnnotationHolder)
  extends ScalaAnnotationHolder {

  protected def element: Option[PsiElement] = None

  protected def transformRange(range: TextRange): TextRange


  override def createErrorAnnotation(elt: PsiElement, message: String): Annotation =
    holder.createErrorAnnotation(transformRange(elt.getTextRange), message)

  override def createErrorAnnotation(node: ASTNode, message: String): Annotation =
    holder.createErrorAnnotation(transformRange(node.getTextRange), message)

  override def createErrorAnnotation(range: TextRange, message: String): Annotation =
    holder.createErrorAnnotation(transformRange(range), message)

  override def createWarningAnnotation(elt: PsiElement, message: String): Annotation =
    holder.createWarningAnnotation(transformRange(elt.getTextRange), message)

  override def createWarningAnnotation(node: ASTNode, message: String): Annotation =
    holder.createWarningAnnotation(transformRange(node.getTextRange), message)

  override def createWarningAnnotation(range: TextRange, message: String): Annotation =
    holder.createWarningAnnotation(transformRange(range), message)

  override def createWeakWarningAnnotation(elt: PsiElement, message: String): Annotation =
    holder.createWeakWarningAnnotation(transformRange(elt.getTextRange), message)

  override def createWeakWarningAnnotation(node: ASTNode, message: String): Annotation =
    holder.createWeakWarningAnnotation(transformRange(node.getTextRange), message)

  override def createWeakWarningAnnotation(range: TextRange, message: String): Annotation =
    holder.createWeakWarningAnnotation(transformRange(range), message)

  override def createInfoAnnotation(elt: PsiElement, message: String): Annotation =
    holder.createInfoAnnotation(transformRange(elt.getTextRange), message)

  override def createInfoAnnotation(node: ASTNode, message: String): Annotation =
    holder.createInfoAnnotation(transformRange(node.getTextRange), message)

  override def createInfoAnnotation(range: TextRange, message: String): Annotation =
    holder.createInfoAnnotation(transformRange(range), message)

  override def createAnnotation(severity: HighlightSeverity, range: TextRange, message: String): Annotation =
    holder.createAnnotation(severity, transformRange(range), message)

  override def createAnnotation(severity: HighlightSeverity, range: TextRange, message: String, htmlTooltip: String): Annotation =
    holder.createAnnotation(severity, transformRange(range), message, htmlTooltip)


  override def getCurrentAnnotationSession: AnnotationSession = session

  override def isBatchMode: Boolean = holder.isBatchMode
}

object DelegateAnnotationHolder {
  def unapply(holder: DelegateAnnotationHolder): Option[PsiElement] = holder.element
}