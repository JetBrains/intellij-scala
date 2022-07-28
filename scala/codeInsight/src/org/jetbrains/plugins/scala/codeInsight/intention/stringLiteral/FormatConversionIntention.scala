package org.jetbrains.plugins.scala
package codeInsight
package intention
package stringLiteral

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.profile.codeInspection.InspectionProfileManager
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.parentheses.ScalaUnnecessaryParenthesesInspection
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.format.{StringConcatenationParser, _}
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.util.MultilineStringUtil

import scala.annotation.tailrec
import scala.collection.mutable

sealed abstract class FormatConversionIntention[P <: StringParser](
  override val getText: String,
  protected val parser: P,
  protected val formatter: StringFormatter
) extends PsiElementBaseIntentionAction {

  override def getFamilyName: String = getText

  protected def findCandidates(element: PsiElement): Seq[PsiElement] =
    element.withParentsInFile.toList

  protected def findTargetIn(element: PsiElement): Option[(PsiElement, Seq[StringPart])] = {
    val candidates = findCandidates(element)
    candidates.iterator
      .map { candidate =>
        val partsOpt = parser.parse(candidate)
        (candidate, partsOpt)
      }
      .collectFirst { case (c, Some(p)) =>
        (c, p)
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
    val replacement = ScalaPsiElementFactory.createExpressionFromText(stringFormatted)(element.getProject)
    target.replace(replacement) match {
      case literal: ScStringLiteral if literal.isMultiLineString =>
        MultilineStringUtil.addMarginsAndFormatMLString(literal, editor.getDocument)
      case _ =>
    }
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

  final class InterpolatedToFormatted extends FormatConversionIntention(
    ConvertToFormatted,
    InterpolatedStringParser,
    FormattedStringFormatter
  )


  final class StringConcatenationToFormatted extends FormatConversionIntention(
    ConvertToFormatted,
    StringConcatenationParser,
    FormattedStringFormatter
  ) with StringConcatenationToAnyBase

  final class StringConcatenationToInterpolated extends FormatConversionIntention(
    ConvertToInterpolated,
    StringConcatenationParser,
    InterpolatedStringFormatter
  ) with StringConcatenationToAnyBase

  trait StringConcatenationToAnyBase {
    self: FormatConversionIntention[StringConcatenationParser.type] =>

    override protected def findCandidates(element: PsiElement): Seq[PsiElement] = {
      val list = element
        .withParentsInFile
        .dropWhile { el => parser.detectOperands(el).isEmpty }
        .takeWhile { el => parser.detectOperands(el).isDefined }
        .toList
      list.reverse
    }
  }

  final class FormattedToStringConcatenation extends FormatConversionIntention(
    ConvertToStringConcat,
    FormattedStringParser,
    StringConcatenationFormatter
  ) with AnyToStringConcatenationBase

  final class InterpolatedToStringConcatenation extends FormatConversionIntention(
    ConvertToStringConcat,
    InterpolatedStringParser,
    StringConcatenationFormatter
  ) with AnyToStringConcatenationBase

  trait AnyToStringConcatenationBase {
    self: FormatConversionIntention[_] =>

    // just run the tests...
    override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = {
      val (target, parts) = findTargetIn(element) match {
        case Some(value) => value
        case _ => return
      }

      implicit val project: Project = element.getProject

      val formattedString = formatter.format(parts)

      import ScalaPsiElementFactory.createExpressionFromText
      val formattedStringExpr0 = createExpressionFromText(formattedString)
      val formattedStringExpr1 = removeUnnecessaryParentheses(formattedStringExpr0)
      val formattedStringExpr2 =
        if (needToWrapConcatenationWithBracketsIn(target))
          createExpressionFromText("(" + formattedStringExpr1.getText + ")")
        else
          formattedStringExpr1

      target.replace(formattedStringExpr2)
    }

    /**
     * SCL-18586
     *
     * @return new element with optimized parenthesis or `formattedStringExpr` if no parentheses were removed
     * @example '''in:''' (str) + (str + str) + (42) + (2 + 2)<br>
     *          '''out:''' str + (str + str) + 42 + (2 + 2)
     */
    private def removeUnnecessaryParentheses(concatenationExpr: ScExpression)
                                                 (implicit project: Project): ScExpression = {
      val elementsWithParenthesis = findElementsWithParenthesis(concatenationExpr)
      removeUnnecessaryParentheses(concatenationExpr, elementsWithParenthesis)
    }

    private def findElementsWithParenthesis(concatenationExpr: ScExpression) = {
      val concatParts: Seq[ScExpression] = concatenationExpr match {
        case concat: ScInfixExpr => flattenConcat(concat)
        case singleExpr          => Seq(singleExpr) // result for simple injection `s"${str}"` is just `str`, not an actual concat
      }

      import ScReferenceExpression.withQualifier
      concatParts.collect {
        case par: ScParenthesisedExpr                                 => par // (2 + 2) + "42"
        case withQualifier(par: ScParenthesisedExpr)                  => par // (str).toString + "42"
        case ScMethodCall(withQualifier(par: ScParenthesisedExpr), _) => par // (2f).formatted("%2.2f") + "42"
      }
    }

    private def removeUnnecessaryParentheses(formattedStringExpr: ScExpression,
                                                  elementsWithParenthesis: Seq[ScParenthesisedExpr])
                                                 (implicit project: Project): ScExpression = {
      val offsetsToRemove = collectUnnecessaryParenthesesOffsets(elementsWithParenthesis, formattedStringExpr.startOffset)
      if (offsetsToRemove.isEmpty)
        formattedStringExpr
      else {
        val newFormattedString = removeChars(formattedStringExpr.getText, offsetsToRemove)
        ScalaPsiElementFactory.createExpressionFromText(newFormattedString)
      }
    }

    private def removeChars(formattedString: String, offsetsToRemove: Seq[Int]): String = {
      val builder = new java.lang.StringBuilder(formattedString.length - offsetsToRemove.size)
      var lastOffset = 0
      offsetsToRemove.foreach { offset =>
        builder.append(formattedString, lastOffset, offset)
        lastOffset = offset + 1
      }
      builder.append(formattedString, lastOffset, formattedString.length)
      builder.toString
    }

    /**
     * @param concatParts parts of concatenation that are wrapped with parentheses
     * @return list of offsets of unnecessary parentheses of `concatParts`<br>
     *         the offsets are relative to the concatenation expression start
     * @example original string: (2 + 2).toString + (str) + (str + str) + 42 + (int)<br>
     *          '''concatParts:''' Seq((2 + 2).toString, (str),  (str + str), (int))<br>
     *          '''out:''' Seq(19, 23, 46, 50) ~ (str) and (int)
     */
    private def collectUnnecessaryParenthesesOffsets(concatParts: Seq[ScParenthesisedExpr], commonOffset: Int)
                                                    (implicit project: Project): Seq[Int] = {
      if (concatParts.isEmpty)
        return Nil

      val inspectionProfile = InspectionProfileManager.getInstance(project).getCurrentProfile
      val wrapper = inspectionProfile.getInspectionTool("ScalaUnnecessaryParentheses", project)
      if (wrapper == null)
        return Nil

      val inspection = wrapper.getTool.asInstanceOf[ScalaUnnecessaryParenthesesInspection]

      var result = mutable.ArrayBuffer.empty[Int]
      concatParts.foreach { el: ScParenthesisedExpr =>
        if (inspection.isParenthesesRedundant(el)) {
          result += el.startOffset - commonOffset
          result += el.endOffset - 1 - commonOffset
        }
      }
      result.toSeq
    }

    /**
     * @example '''in: ''' 0 + 1 * 2 + 3 * 4<br>
     *          '''out:''' Seq(0, 1 * 2, 3 * 4)
     */
    @tailrec
    private def flattenConcat(infix: ScInfixExpr, acc: List[ScExpression] = Nil): Seq[ScExpression] = {
      val left = infix.left
      val right = infix.right
      left match {
        case inner@Concat(_, _) => flattenConcat(inner, right :: acc)
        case _                  => left :: right :: acc
      }
    }
  }

  private object Concat {
    def unapply(infix: ScInfixExpr): Option[(ScExpression, ScExpression)] =
      infix match {
        case ScInfixExpr(left, ElementText("+"), right) => Some((left, right))
        case _                                          => None
      }
  }

  /** @param targetToReplace original element which will be replaced with concatenation */
  private def needToWrapConcatenationWithBracketsIn(targetToReplace: PsiElement) =
    targetToReplace.getParent match {
      // in case of postfix/infix expressions brackets are not always necessary, but the code becomes readable
      case postfix: ScPostfixExpr if postfix.operand == targetToReplace          => true
      case ref: ScReferenceExpression if ref.qualifier.contains(targetToReplace) => true
      case infix: ScInfixExpr if infix.rightOption.contains(targetToReplace)     => true
      case _                                                                     => false
    }
}
