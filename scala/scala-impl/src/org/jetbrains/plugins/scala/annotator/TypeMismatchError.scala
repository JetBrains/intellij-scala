package org.jetbrains.plugins.scala.annotator

import com.intellij.lang.annotation.{Annotation, AnnotationHolder}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.{Key, TextRange}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.annotator.quickfix.ReportHighlightingErrorQuickFix
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockExpr
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.ScTypePresentation

case class TypeMismatchError(expectedType: Option[ScType], actualType: Option[ScType], message: String, modificationCount: Long)

object TypeMismatchError {
  private val TypeMismatchErrorKey = Key.create[TypeMismatchError]("TypeMismatch")

  def apply(element: PsiElement): Option[TypeMismatchError] = Option(element.getUserData(TypeMismatchErrorKey))

  def register(holder: AnnotationHolder, element: PsiElement, expectedType: ScType, actualType: ScType, blockLevel: Int = 0)(formatMessage: (String, String) => String): Annotation = {
    val message = {
      val (actualTypeText, expectedTypeText) = ScTypePresentation.different(actualType, expectedType)
      formatMessage(expectedTypeText, actualTypeText)
    }

    val annotatedElement = blockLevel match {
      case 2 =>
        (element, element.getParent) match {
          case (b: ScBlockExpr, _) => b.getRBrace.map(_.getPsi).getOrElse(element)
          case (_, b: ScBlockExpr) => b.getRBrace.map(_.getPsi).getOrElse(element)
          case _ => element
        }
      case 1 =>
        element match {
          case b: ScBlockExpr => b.getRBrace.map(_.getPsi).getOrElse(b)
          case _ => element
        }
      case 0 => element
    }

    // TODO type mismatch hints are experimental, don't affect annotator / highlighting tests
    val annotation: Annotation = if (ApplicationManager.getApplication.isUnitTestMode ||
      TypeMismatchHighlightingMode.in(element.getProject) == TypeMismatchHighlightingMode.HIGHLIGHT_EXPRESSION) {
      holder.createErrorAnnotation(annotatedElement, message)
    } else {
      val annotation = holder.createErrorAnnotation(lastLineRangeOf(annotatedElement), message)
      adjustTextAttributesOf(annotation)
      annotation
    }

    annotation.registerFix(ReportHighlightingErrorQuickFix)

    annotatedElement.putUserData(TypeMismatchErrorKey, TypeMismatchError(Some(expectedType), Some(actualType), message, element.getManager.getModificationTracker.getModificationCount))

    annotation
  }

  def clear(element: PsiElement): Unit = {
    val annotatedElement = (element, element.getParent) match {
      case (b: ScBlockExpr, _) => b.getRBrace.map(_.getPsi).getOrElse(element)
      case (_, b: ScBlockExpr) => b.getRBrace.map(_.getPsi).getOrElse(element)
      case _ => element
    }
    annotatedElement.putUserData(TypeMismatchErrorKey, null)
  }

  private def adjustTextAttributesOf(annotation: Annotation) = {
    val errorStripeColor = annotation.getTextAttributes.getDefaultAttributes.getErrorStripeColor
    val attributes = new TextAttributes()
    attributes.setEffectType(null)
    attributes.setErrorStripeColor(errorStripeColor)
    annotation.setEnforcedTextAttributes(attributes)
  }

  private def lastLineRangeOf(element: PsiElement) = {
    val range = element.getTextRange
    val text = element.getText
    val lastLineOffset = range.getStartOffset + Option(text.lastIndexOf("\n")).filterNot(_ == -1).map(_ + 1).getOrElse(0)
    TextRange.create(lastLineOffset, range.getEndOffset)
  }
}
