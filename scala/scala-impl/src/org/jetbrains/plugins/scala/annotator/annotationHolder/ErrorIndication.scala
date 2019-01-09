package org.jetbrains.plugins.scala.annotator.annotationHolder

import com.intellij.lang.annotation.{Annotation, AnnotationHolder, HighlightSeverity}
import com.intellij.openapi.util.TextRange

import scala.math.Ordering.Implicits._

trait ErrorIndication extends AnnotationHolder {
  private[this] var _hadError = false

  def hadError: Boolean = _hadError

  abstract override def createAnnotation(severity: HighlightSeverity, range: TextRange, message: String, htmlTooltip: String): Annotation = {
    if (severity >=  HighlightSeverity.ERROR) {
      _hadError = true
    }
    super.createAnnotation(severity, range, message, htmlTooltip)
  }
}
