package org.jetbrains.plugins.scala.annotator.annotationHolder

import com.intellij.codeInsight.daemon.impl.AnnotationHolderImpl
import com.intellij.lang.annotation.{Annotation, AnnotationHolder, AnnotationSession, HighlightSeverity}
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

class DelegateAnnotationHolder(transformRange: TextRange => TextRange, holder: AnnotationHolder, session: AnnotationSession, private val element: Option[PsiElement] = None)
  extends AnnotationHolderImpl(session, holder.isBatchMode) {

  def this(elem: PsiElement, holder: AnnotationHolder, session: AnnotationSession) = this(_ => elem.getTextRange, holder, session, Some(elem))

  override def createAnnotation(severity: HighlightSeverity, range: TextRange, message: String, tooltip: String): Annotation = {
    holder.createAnnotation(severity, transformRange(range), message, tooltip)
  }
}

object DelegateAnnotationHolder {
  def unapply(holder: DelegateAnnotationHolder): Option[PsiElement] = holder.element
}