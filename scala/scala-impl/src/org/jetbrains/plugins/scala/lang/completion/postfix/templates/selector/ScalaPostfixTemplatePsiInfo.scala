package org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplatePsiInfo
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.booleans.SimplifyBooleanUtil
import org.jetbrains.plugins.scala.codeInspection.parentheses.UnnecessaryParenthesesUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScParenthesisedExpr, ScPrefixExpr, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionFromText
import org.jetbrains.plugins.scala.lang.psi.types.api.Boolean
import org.jetbrains.plugins.scala.lang.surroundWith.surrounders.expression.ScalaWithUnaryNotSurrounder

/**
 * @author Roman.Shein
 * @since 09.09.2015.
 */
object ScalaPostfixTemplatePsiInfo extends PostfixTemplatePsiInfo {

  private val notSurrounder = new ScalaWithUnaryNotSurrounder() {
    override def getTemplateAsString(elements: Array[PsiElement]): String =
      elements.headOption.map {
        case _: ScLiteral | _: ScReferenceExpression | _: ScParenthesisedExpr if elements.length == 1 =>
          "!" + templateText(elements)
        case ScPrefixExpr(operation, operand) if operation.refName == "!" &&
          operand.`type`().getOrAny.conforms(Boolean(operand.projectContext)) =>
          operand.getNode.getText
        case _ => s"!(${templateText(elements)})"
      }.getOrElse("!()")

    private def templateText(elements: Array[PsiElement]): String =
      elements.foldLeft(""){case (acc, elem) => acc + elem.getNode.getText}

    override def needParenthesis(parent: PsiElement): Boolean = super.needParenthesis(parent) &&
      //are in boolean operation expr (operation priorities are known) we don't spend time on removing parentheses
      (parent match {
        case expr: ScExpression => !SimplifyBooleanUtil.isBooleanOperation(expr)
        case _ => true
      })

    override def surroundPsi(elements: Array[PsiElement]): ScExpression = {
      SimplifyBooleanUtil.simplify(super.surroundPsi(elements), isTopLevel = false) match {
        case parenthesized: ScParenthesisedExpr
          if UnnecessaryParenthesesUtil.canBeStripped(parenthesized, ignoreClarifying = false) =>
          val stripped = UnnecessaryParenthesesUtil.getTextOfStripped(parenthesized, ignoreClarifying = false)
          createExpressionFromText(stripped)(parenthesized)
        case other => other
      }
    }
  }

  override def getNegatedExpression(element: PsiElement): ScExpression =
    element match {
      case expr if notSurrounder.isApplicable(Array(expr)) =>
        notSurrounder.surroundPsi(Array(element))
      case _ => throw new IllegalArgumentException("Attempted adding negation through template for element " + element.getText
              + " which is not a valid boolean expression.")
    }

  override def createExpression(context: PsiElement, prefix: String, suffix: String): PsiElement =
    createExpressionFromText(prefix + context.getText + suffix, context)
}
