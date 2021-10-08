package org.jetbrains.plugins.scala
package annotator
package annotationHolder

import com.intellij.lang.annotation.{AnnotationSession, HighlightSeverity}
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

abstract class DelegateAnnotationHolder(session: AnnotationSession)
                                       (implicit holder: ScalaAnnotationHolder)
  extends ScalaAnnotationHolder {

  protected def element: Option[PsiElement] = None

  protected def transformRange(range: TextRange): TextRange

  override def getCurrentAnnotationSession: AnnotationSession = session

  override def isBatchMode: Boolean = holder.isBatchMode

  override def newAnnotation(severity: HighlightSeverity, message: String): ScalaAnnotationBuilder =
    holder.newAnnotation(severity, message).setRangeTransformer(transformRange)

  override def newSilentAnnotation(severity: HighlightSeverity): ScalaAnnotationBuilder =
    holder.newSilentAnnotation(severity).setRangeTransformer(transformRange)
}

object DelegateAnnotationHolder {
  def unapply(holder: DelegateAnnotationHolder): Option[PsiElement] = holder.element
}