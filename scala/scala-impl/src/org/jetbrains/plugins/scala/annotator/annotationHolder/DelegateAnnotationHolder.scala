package org.jetbrains.plugins.scala
package annotator
package annotationHolder

import com.intellij.codeInsight.daemon.impl.AnnotationHolderImpl
import com.intellij.lang.annotation.{Annotation, AnnotationHolder, AnnotationSession, HighlightSeverity}
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

abstract class DelegateAnnotationHolder(session: AnnotationSession)
                                       (implicit holder: AnnotationHolder)
  extends AnnotationHolderImpl(session, holder.isBatchMode) {

  protected val element: Option[PsiElement] = None

  override def createAnnotation(severity: HighlightSeverity, range: TextRange, message: String, tooltip: String): Annotation =
    holder.createAnnotation(
      severity,
      transformRange(range),
      message,
      tooltip
    )

  protected def transformRange(range: TextRange): TextRange
}

object DelegateAnnotationHolder {
  def unapply(holder: DelegateAnnotationHolder): Option[PsiElement] = holder.element
}