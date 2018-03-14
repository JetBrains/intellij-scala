package org.jetbrains.plugins.scala
package codeInspection.parentheses

import javax.swing.JComponent

import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel
import com.intellij.codeInspection.{ProblemHighlightType, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInsight.intention.IntentionUtil
import org.jetbrains.plugins.scala.codeInspection.parentheses.UnnecessaryParenthesesUtil._
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, AbstractInspection, InspectionBundle}
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScParenthesisedTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml.ScXmlPattern
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiElement, ScalaPsiUtil}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScTypeUtil
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil.getShortText
import org.jetbrains.plugins.scala.util.IntentionAvailabilityChecker.checkInspection

import scala.annotation.tailrec

/**
 * Nikolay.Tropin
 * 4/25/13
 */
abstract class ScalaUnnecessaryParenthesesInspectionBase extends AbstractInspection("UnnecessaryParenthesesU", "Remove unnecessary parentheses") {


  override def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case expr: ScParenthesisedExpr if isProblem(classOf[ScParenthesisedExpr], expr, canBeStripped) =>

      holder.registerProblem(expr, "Unnecessary parentheses", ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                             new UnnecessaryParenthesesQuickFix(expr, getTextOfStripped(expr, getIgnoreClarifying)))

    case typeElt: ScParenthesisedTypeElement if isProblem(classOf[ScParenthesisedTypeElement], typeElt, canTypeBeStripped) => registerProblem(typeElt)
    case pattern: ScParenthesisedPattern if isProblem(classOf[ScParenthesisedPattern], pattern, canPatternBeStripped) => registerProblem(pattern)
  }

  override def createOptionsPanel(): JComponent = {
    new SingleCheckboxOptionsPanel(InspectionBundle.message("ignore.clarifying.parentheses"), this, "ignoreClarifying")
  }

  def getIgnoreClarifying: Boolean
  def setIgnoreClarifying(value: Boolean)


  private def isProblem[T <: ScalaPsiElement](clazz: Class[T], elem: T, canBeStripped: (T, Boolean) => Boolean): Boolean
  = !clazz.isInstance(elem.getParent) && checkInspection(this, elem) && canBeStripped(elem, getIgnoreClarifying)


  private def registerProblem(elt: ScalaPsiElement)(implicit holder: ProblemsHolder): Unit = {
    holder.registerProblem(elt, "Unnecessary parentheses", ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                           new UnnecessaryParenthesesTypeOrPatternQuickFix(elt, getIgnoreClarifying))
  }


}



class UnnecessaryParenthesesQuickFix(parenthesized: ScParenthesisedExpr, textOfStripped: String)
        extends AbstractFixOnPsiElement("Remove unnecessary parentheses " + getShortText(parenthesized), parenthesized){

  override protected def doApplyFix(parenthExpr: ScParenthesisedExpr)(implicit project: Project): Unit = {
    val newExpr = createExpressionFromText(textOfStripped)
    val replaced = parenthExpr.replaceExpression(newExpr, removeParenthesis = true)

    val comments = Option(parenthExpr.expr.get).map(expr => IntentionUtil.collectComments(expr))
    comments.foreach(value => IntentionUtil.addComments(value, replaced.getParent, replaced))

    ScalaPsiUtil.padWithWhitespaces(replaced)
  }
}

class UnnecessaryParenthesesTypeOrPatternQuickFix(parenthesized: ScalaPsiElement, ignoreClarifying: Boolean)
  extends AbstractFixOnPsiElement("Remove unnecessary parentheses " + getShortText(parenthesized), parenthesized) {

  import UnnecessaryParenthesesTypeOrPatternQuickFix._

  override protected def doApplyFix(element: ScalaPsiElement)(implicit project: Project): Unit = {
    element match {
      case typeElt: ScParenthesisedTypeElement => typeQuickFix(ignoreClarifying, typeElt)
      case pattern: ScParenthesisedPattern => patternQuickFix(ignoreClarifying, pattern)
      case _ =>
    }
  }
}

// companion
object UnnecessaryParenthesesTypeOrPatternQuickFix {

  private def applyFixTemplate[T <: ScalaPsiElement, P <: T : Manifest](getSubElem: P => Option[T],
                                                                        stripParen: (P, Boolean) => T,
                                                                        canBeStripped: (P, Boolean) => Boolean)
                                                                       (ignoreClarifying: Boolean, element: P): Unit = {

    val keepParentheses = getSubElem(element).exists(_.isInstanceOf[P])
    val replaced: T = stripParen(element, keepParentheses) match {
        // Remove the last level of parentheses if allowed
      case paren: P if canBeStripped(paren, ignoreClarifying) => stripParen(paren, false)
        case other => other
    }

    val comments = getSubElem(element).map(expr => IntentionUtil.collectComments(expr))
    comments.foreach(IntentionUtil.addComments(_, replaced.getParent, replaced))

    ScalaPsiUtil padWithWhitespaces replaced
  }


  private val patternQuickFix = applyFixTemplate[ScPattern, ScParenthesisedPattern](_.subpattern, _.stripParentheses(_), canPatternBeStripped) _
  private val typeQuickFix = applyFixTemplate[ScTypeElement, ScParenthesisedTypeElement](_.typeElement, _.stripParentheses(_), canTypeBeStripped) _
}

object UnnecessaryParenthesesUtil {

  def canBeStripped(parenthesized: ScParenthesisedExpr, ignoreClarifying: Boolean): Boolean
  = canBeStripped[ScParenthesisedExpr](ignoreClarifying, parenthesized,
                                       expressionParenthesesAreClarifying,
                                       p => ScalaPsiUtil.needParentheses(p, p.expr.get))


  private def expressionParenthesesAreClarifying(p: ScParenthesisedExpr)
  = (p.getParent, p.expr.get) match {
    case (_: ScSugarCallExpr, _: ScSugarCallExpr) => true
    case _ => false
  }

  def canTypeBeStripped(parenthesizedType: ScParenthesisedTypeElement, ignoreClarifying: Boolean): Boolean
  = canBeStripped[ScParenthesisedTypeElement](ignoreClarifying, parenthesizedType,
                                              typeParenthesesAreClarifying,
                                              ScTypeUtil.typeNeedsParentheses)


  private def typeParenthesesAreClarifying(p: ScParenthesisedTypeElement) = {
    import ScTypeUtil.getPrecedence

    (p.getParent, p.typeElement) match {
      case (parent: ScTypeElement, Some(child)) if getPrecedence(child) < ScTypeUtil.HighestPrecedence && getPrecedence(parent) != getPrecedence(child) => true
      case _ => false
    }
  }


  def canPatternBeStripped(parenthesizedPattern: ScParenthesisedPattern, ignoreClarifying: Boolean): Boolean =
    canBeStripped[ScParenthesisedPattern](ignoreClarifying, parenthesizedPattern,
                                          patternParenthesesAreClarifying,
                                          ScPatternUtil.patternNeedsParentheses)


  private def patternParenthesesAreClarifying(p: ScParenthesisedPattern): Boolean = {
    import ScPatternUtil.getPrecedence

    (p.getParent, p.subpattern) match {
      case (_: ScCompositePattern | _: ScNamingPattern | _: ScTuplePattern, _) => false
      case (parent: ScPattern, Some(child)) if getPrecedence(child) < ScPatternUtil.HighestPrecedence && getPrecedence(parent) != getPrecedence(child) => true
      case _ => false
    }
  }


  private def canBeStripped[T](ignoreClarifying: Boolean, parenthesized: T, isClarifying: T => Boolean, needsParen: T => Boolean): Boolean
  = (!ignoreClarifying || ignoreClarifying && !isClarifying(parenthesized)) && !needsParen(parenthesized)


  @tailrec
  def getTextOfStripped(expr: ScExpression, ignoreClarifying: Boolean): String = expr match {
    case parenthesized @ ScParenthesisedExpr(inner) if canBeStripped(parenthesized, ignoreClarifying) =>
      getTextOfStripped(inner, ignoreClarifying)
    case _ => expr.getText
  }
}


object ScPatternUtil {

  val HighestPrecedence: Int = 12
  val LowestPrecedence: Int = 0


  /** Gets the precedence of a pattern, much like [[ScTypeUtil.getPrecedence]]
    * does for type elements.
    *
    * Predence of infix patterns must account for the precedence given
    * by the first character of the operator, using the same rules
    * as for expressions (see [[ParserUtils.priority]]).
    *
    * Patterns with precedence [[HighestPrecedence]] are indivisible.
    *
    * @param pattern The pattern to check for
    * @return The precedence. Higher is applied first.
    */
  def getPrecedence(pattern: ScPattern): Int = pattern match {
    case _: ScCompositePattern => 0
    case _: ScNamingPattern => 1
    case ScInfixPattern(_, ifxOp, _) => 11 - ParserUtils.priority(ifxOp.getText) // varies from 2 to 11
    case _ =>  HighestPrecedence
  }


  def patternNeedsParentheses(parPattern: ScParenthesisedPattern): Boolean = {
    import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.InfixExpr.associate

    val parent = parPattern.getParent
    val subPattern = parPattern.subpattern.get

    (parent, subPattern) match {
      // highest precedence => indivisible
      case (_, c) if getPrecedence(c) == HighestPrecedence => false

      case (p: ScPattern, c) if getPrecedence(p) > getPrecedence(c) => true
      case (p: ScPattern, c) if getPrecedence(p) < getPrecedence(c) => false

      // Infix pattern chain with same precedence:
      // - If the two operators have different associativities, then the parentheses are required
      // - If they have the same associativity, then right- or left- associativity applies depending on the operator
      case (ScInfixPattern(_, ifx, _), ScInfixPattern(_, ifx2, _)) if associate(ifx.getText) != associate(ifx2.getText) => true

      case (ScInfixPattern(_, ifxOp, Some(`parPattern`)), _: ScInfixPattern) => associate(ifxOp.getText) == +1
      case (ScInfixPattern(`parPattern`, ifxOp, _), _: ScInfixPattern) => associate(ifxOp.getText) == -1

      case _ => false
    }
  }
}