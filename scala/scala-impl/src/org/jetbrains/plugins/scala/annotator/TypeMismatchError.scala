package org.jetbrains.plugins.scala.annotator

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.annotation.{HighlightSeverity, ProblemGroup}
import com.intellij.openapi.editor.colors.{EditorColorsManager, EditorColorsScheme}
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.annotator.annotationHolder.DelegateAnnotationHolder
import org.jetbrains.plugins.scala.annotator.hints.onlyErrorStripeAttributes
import org.jetbrains.plugins.scala.annotator.quickfix.{EnableTypeMismatchHints, ReportHighlightingErrorQuickFix}
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScMatchTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.psi.types.api.ParameterizedType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.DesignatorOwner
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.TypePresentation
import org.jetbrains.plugins.scala.lang.psi.types.{AliasType, Context, ScLiteralType, ScType, TypePresentationContext}
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.{ScalaBundle, isUnitTestMode}

private object TypeMismatchError {
  def register(element: PsiElement, expectedType: ScType, actualType: ScType,
               blockLevel: Int = 0, canBeHint: Boolean = true,
               fixes: Iterable[(IntentionAction, TextRange)] = Nil)
              (formatMessage: (String, String) => String)
              (implicit holder: ScalaAnnotationHolder): Unit = {
    val annotatedElement = elementAt(element, blockLevel)
    implicit val tpc: TypePresentationContext = TypePresentationContext(annotatedElement)
    implicit val context: Context = Context(element)

    val dealiasedMatchTypeInExpected = expectedType match {
      case ParameterizedType(DesignatorOwner(ta: ScTypeAliasDefinition), _) if
        ta.aliasedTypeElement.exists(_.is[ScMatchTypeElement]) => expectedType.removeAliasDefinitions()
      case other => other
    }

    // TODO update the test data, SCL-15483
    val adjustedActualType = (dealiasedMatchTypeInExpected, actualType) match {
      case (_: ScLiteralType, t2: ScLiteralType) => t2
      case (_, t2: ScLiteralType) => t2.wideType
      case (_, t2) => t2
    }

    val message = {
      val (actualTypeText, expectedTypeText) = TypePresentation.different(adjustedActualType, dealiasedMatchTypeInExpected)

      if (isUnitTestMode) formatMessage(expectedTypeText, actualTypeText)
      else ScalaBundle.message("type.mismatch.message", expectedTypeText, actualTypeText)
    }

    val addHint = ScalaProjectSettings.in(element.getProject).isTypeMismatchHints && canBeHint
    val addHighlighting = !addHint || isUnitTestMode

    // TODO Can we detect a "current" color scheme in a "current" editor somehow?
    implicit val scheme: EditorColorsScheme = EditorColorsManager.getInstance().getGlobalScheme

    val tooltip = TypeMismatchHints.tooltipFor(dealiasedMatchTypeInExpected, adjustedActualType)
    val textRange =
      if (addHighlighting) {
        annotatedElement.getTextRange
      } else {
        // we only need range for error stripe, and it should be inside `element`
        val lastLineAnnotatedRange = lastLineRangeOf(annotatedElement)
        val intersection = lastLineAnnotatedRange.intersection(element.getTextRange)

        if (intersection != null && intersection.getLength > 0) intersection
        else lastLineRangeOf(element)
      }

    val enforcedTextAttr = Option.unless(addHighlighting)(onlyErrorStripeAttributes)

    val builder = holder.newAnnotation(HighlightSeverity.ERROR, message)
      .tooltip(tooltip)
      .withFix(ReportHighlightingErrorQuickFix)
      .withFix(EnableTypeMismatchHints)
      .problemGroup(TypeMismatchErrorProblemGroup)

    for ((fix, range) <- fixes) {
      builder.newFix(fix).range(range).registerFix
    }
    builder.range(textRange)
    enforcedTextAttr.foreach(builder.enforcedTextAttributes)

    builder.create()

    if (addHint) {
      val delegateElement = holder match {
        // handle possible element mapping (e.g. ScGeneratorAnnotator)
        case DelegateAnnotationHolder(element) => element
        case _ => annotatedElement
      }

      TypeMismatchHints.createFor(delegateElement, dealiasedMatchTypeInExpected, adjustedActualType).putTo(delegateElement)
    }
  }

  private def elementAt(element: PsiElement, blockLevel: Int) = blockLevel match {
    case 2 =>
      (element, element.getParent) match {
        case (b: ScBlockExpr, _) => b.getRBrace.getOrElse(element)
        case (_, b: ScBlockExpr) => b.getRBrace.getOrElse(element)
        case _ => element
      }
    case 1 =>
      element match {
        case b: ScBlockExpr => b.getRBrace.getOrElse(b)
        case _ => element
      }
    case 0 => element
  }

  private def lastLineRangeOf(element: PsiElement) = {
    val range = element.getTextRange
    val text = element.getText
    val lastLineOffset = range.getStartOffset + Option(text.lastIndexOf("\n")).filterNot(_ == -1).map(_ + 1).getOrElse(0)
    TextRange.create(lastLineOffset, range.getEndOffset)
  }

  object TypeMismatchErrorProblemGroup extends ProblemGroup {
    override def getProblemName: String = "ScalaTypeMismatch"
  }
}
