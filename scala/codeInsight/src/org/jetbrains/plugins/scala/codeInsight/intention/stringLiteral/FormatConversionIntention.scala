package org.jetbrains.plugins.scala
package codeInsight
package intention
package stringLiteral

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.format._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScInfixExpr, ScPostfixExpr, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.util.MultilineStringUtil

/**
 * Pavel Fatin
 */
sealed abstract class FormatConversionIntention(override val getText: String,
                                                parser: StringParser,
                                                formatter: StringFormatter) extends PsiElementBaseIntentionAction {

  override def getFamilyName: String = getText

  protected def eager: Boolean = false

  private def findTargetIn(element: PsiElement): Option[(PsiElement, Seq[StringPart])] = {
    val candidates = element.withParentsInFile.toList match {
      case list if eager => list.reverse
      case list => list
    }

    candidates.collectFirst {
      case candidate@Parts(parts) => (candidate, parts)
    }
  }

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean =
    findTargetIn(element).isDefined

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = {
    val (target, parts) = findTargetIn(element) match {
      case Some(value) => value
      case _ => return
    }

    val stringFormatted = formatter.format(parts)
    val replacement = ScalaPsiElementFactory.createExpressionFromText(stringFormatted)(element.getManager)

    val needToWrapConcatWithBrackets = target.getParent match {
      case ref: ScReferenceExpression if ref.qualifier.contains(target) => true
      // in case of postfix/infix expressions brackets are not necessary, but the code becomes readable
      case postfix: ScPostfixExpr if postfix.operand == target => true
      case infix: ScInfixExpr if infix.rightOption.contains(target) => true
      case _ => false
    }

    target.replace(replacement) match {
      case literal: ScLiteral if literal.isMultiLineString =>
        MultilineStringUtil.addMarginsAndFormatMLString(literal, editor.getDocument)
      case concat: ScInfixExpr if needToWrapConcatWithBrackets =>
        surroundWithBrackets(concat, editor.getDocument)
      case _ =>
    }
  }

  private def surroundWithBrackets(element: PsiElement, document: Document): Unit = {
    PsiDocumentManager.getInstance(element.getProject).doPostponedOperationsAndUnblockDocument(document)
    inWriteAction {
      val range = element.getTextRange
      document.insertString(range.getEndOffset, ")")
      document.insertString(range.getStartOffset, "(")
    }
  }

  private object Parts {
    def unapply(element: PsiElement): Option[Seq[StringPart]] = parser.parse(element)
  }

}

object FormatConversionIntention {
  val ConvertToStringConcat: String = ScalaCodeInsightBundle.message("convert.to.string.concatenation")
  val ConvertToInterpolated: String = ScalaCodeInsightBundle.message("convert.to.interpolated.string")
  val ConvertToFormatted: String = ScalaCodeInsightBundle.message("convert.to.formatted.string")

  final class FormattedToInterpolated extends FormatConversionIntention(
    ConvertToInterpolated,
    FormattedStringParser,
    InterpolatedStringFormatter
  )

  final class FormattedToStringConcatenation extends FormatConversionIntention(
    ConvertToStringConcat,
    FormattedStringParser,
    StringConcatenationFormatter
  )

  final class InterpolatedToFormatted extends FormatConversionIntention(
    ConvertToFormatted,
    InterpolatedStringParser,
    FormattedStringFormatter
  )

  final class InterpolatedToStringConcatenation extends FormatConversionIntention(
    ConvertToStringConcat,
    InterpolatedStringParser,
    StringConcatenationFormatter
  )

  final class StringConcatenationToFormatted extends FormatConversionIntention(
    ConvertToFormatted,
    StringConcatenationParser,
    FormattedStringFormatter
  ) {
    override protected def eager: Boolean = true
  }

  final class StringConcatenationToInterpolated extends FormatConversionIntention(
    ConvertToInterpolated,
    StringConcatenationParser,
    InterpolatedStringFormatter
  ) {
    override protected def eager: Boolean = true
  }

}
