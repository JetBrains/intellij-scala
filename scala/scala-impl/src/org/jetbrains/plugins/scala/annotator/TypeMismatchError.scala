package org.jetbrains.plugins.scala.annotator

import com.intellij.lang.annotation.{Annotation, AnnotationHolder}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.{Key, TextRange}
import com.intellij.psi.PsiElement
import com.intellij.util.ui.UIUtil
import com.intellij.xml.util.XmlStringUtil.escapeString
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.TypeDiff.{Match, Mismatch}
import org.jetbrains.plugins.scala.annotator.annotationHolder.DelegateAnnotationHolder
import org.jetbrains.plugins.scala.annotator.quickfix.ReportHighlightingErrorQuickFix
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockExpr
import org.jetbrains.plugins.scala.lang.psi.types.api.ScTypePresentation
import org.jetbrains.plugins.scala.lang.psi.types.{ScLiteralType, ScType}

case class TypeMismatchError(expectedType: Option[ScType], actualType: Option[ScType], tooltip: String, modificationCount: Long)

object TypeMismatchError {
  private val TypeMismatchErrorKey = Key.create[TypeMismatchError]("TypeMismatch")

  def apply(element: PsiElement): Option[TypeMismatchError] = Option(element.getUserData(TypeMismatchErrorKey))

  def register(holder: AnnotationHolder, element: PsiElement, expectedType: ScType, actualType: ScType, blockLevel: Int = 0)(formatMessage: (String, String) => String): Annotation = {
    // When expected type is not literal type, widen actual literal type (as it looks more sane)
    val wideActualType = (expectedType, actualType) match {
      case (_: ScLiteralType, t2: ScLiteralType) => t2
      // TODO update the test data, SCL-15571
      case (_, t2: ScLiteralType) if !ApplicationManager.getApplication.isUnitTestMode => t2.wideType
      case (_, t2) => t2
    }
    register0(holder, element, expectedType, wideActualType, blockLevel)(formatMessage)
  }

  private def register0(holder: AnnotationHolder, element: PsiElement, expectedType: ScType, actualType: ScType, blockLevel: Int)(formatMessage: (String, String) => String): Annotation = {
    val (actualTypeText, expectedTypeText) = ScTypePresentation.different(actualType, expectedType)

    // TODO update the test data, SCL-15483
    val message =
      if (ApplicationManager.getApplication.isUnitTestMode) formatMessage(expectedTypeText, actualTypeText)
      else ScalaBundle.message("type.mismatch.message", expectedTypeText, actualTypeText)

    val annotatedElement = elementAt(element, blockLevel)

    val highlightExpression = TypeMismatchHighlightingMode.in(element.getProject) == TypeMismatchHighlightingMode.HIGHLIGHT_EXPRESSION

    // TODO type mismatch hints are experimental (SCL-15250), don't affect annotator / highlighting tests
    val annotation = if (ApplicationManager.getApplication.isUnitTestMode || highlightExpression) {
      holder.createErrorAnnotation(annotatedElement, message)
    } else {
      val annotation = holder.createErrorAnnotation(lastLineRangeOf(annotatedElement), message)
      adjustTextAttributesOf(annotation)
      annotation
    }

    annotation.setTooltip(if (highlightExpression) typeMismatchTooltipFor(expectedType, actualType) else null)
    annotation.registerFix(ReportHighlightingErrorQuickFix)

    val error = TypeMismatchError(
      Some(expectedType), Some(actualType),
      typeMismatchTooltipFor(expectedType, actualType),
      element.getManager.getModificationTracker.getModificationCount)

    val dataHolder = holder match {
      case DelegateAnnotationHolder(e) => e
      case _ => annotatedElement
    }

    dataHolder.putUserData(TypeMismatchErrorKey, error)

    annotation
  }

  private def typeMismatchTooltipFor(expectedType: ScType, actualType: ScType): String = {
    def format(diff: TypeDiff, f: String => String) = {
      val parts = diff.flatten.map {
        case Match(text, _) => text
        case Mismatch(text, _) => f(text)
      } map {
        "<td style=\"text-align:center\">" + _ + "</td>"
      }
      parts.mkString
    }

    // com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil.redIfNotMatch
    def red(text: String) = {
      val color = if (UIUtil.isUnderDarcula) "FF6B68" else "red"
      "<font color='" + color + "'><b>" + escapeString(text) + "</b></font>"
    }

    val (diff1, diff2) = TypeDiff.forBoth(expectedType, actualType)

    ScalaBundle.message("type.mismatch.tooltip", format(diff1, s => s"<b>$s</b>"), format(diff2, red))
  }

  def clear(element: PsiElement): Unit = {
    element.putUserData(TypeMismatchErrorKey, null)
  }

  private def elementAt(element: PsiElement, blockLevel: Int) = blockLevel match {
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
