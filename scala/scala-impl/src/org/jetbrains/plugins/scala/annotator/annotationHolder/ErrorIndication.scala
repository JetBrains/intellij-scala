package org.jetbrains.plugins.scala
package annotator
package annotationHolder

import com.intellij.lang.ASTNode
import com.intellij.lang.annotation.{Annotation, HighlightSeverity}
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

import scala.math.Ordering.Implicits._

trait ErrorIndication extends ScalaAnnotationHolder {
  private[this] var _hadError = false

  def hadError: Boolean = _hadError

  abstract override def createErrorAnnotation(elt: PsiElement, message: String): Annotation = {
    _hadError = true
    super.createErrorAnnotation(elt, message)
  }

  abstract override def createErrorAnnotation(node: ASTNode, message: String): Annotation = {
    _hadError = true
    super.createErrorAnnotation(node, message)
  }

  abstract override def createErrorAnnotation(range: TextRange, message: String): Annotation = {
    _hadError = true
    super.createErrorAnnotation(range, message)
  }

  abstract override def createAnnotation(severity: HighlightSeverity, range: TextRange, message: String, htmlTooltip: String): Annotation = {
    if (severity >=  HighlightSeverity.ERROR) {
      _hadError = true
    }
    super.createAnnotation(severity, range, message, htmlTooltip)
  }
}
