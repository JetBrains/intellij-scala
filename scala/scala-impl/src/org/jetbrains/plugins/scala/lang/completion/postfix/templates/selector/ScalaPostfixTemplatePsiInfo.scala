package org.jetbrains.plugins.scala.lang
package completion
package postfix
package templates
package selector

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplatePsiInfo
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.booleans.SimplifyBooleanUtil
import org.jetbrains.plugins.scala.extensions.ParenthesizedElement.Ops
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.api
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.lang.surroundWith.surrounders.expression.ScalaWithUnaryNotSurrounder
import org.jetbrains.plugins.scala.project.ProjectContext

/**
  * @author Roman.Shein
  * @since 09.09.2015.
  */
object ScalaPostfixTemplatePsiInfo extends PostfixTemplatePsiInfo {

  import ScalaPsiElementFactory.createExpressionFromText

  override def getNegatedExpression(element: PsiElement): ScExpression = NotSurrounder(element)

  override def createExpression(context: PsiElement, prefix: String, suffix: String): PsiElement =
    createExpressionFromText(prefix + context.getText + suffix, context)

  private object NotSurrounder extends ScalaWithUnaryNotSurrounder {

    import SimplifyBooleanUtil.{isBooleanOperation, simplify}

    def apply(element: PsiElement): ScExpression = {
      val elements = Array(element)
      if (!isApplicable(elements)) throw new IllegalArgumentException(s"Attempted adding negation through template for element ${element.getText} which is not a valid boolean expression.")

      simplify(super.surroundPsi(elements), isTopLevel = false) match {
        case parenthesized: ScParenthesisedExpr if parenthesized.isParenthesisRedundant =>
          implicit val context: ProjectContext = parenthesized
          createExpressionFromText(parenthesized.getTextOfStripped())
        case expression => expression
      }
    }

    override def getTemplateAsString(elements: Array[PsiElement]): String = elements match {
      case Array(expression@(_: ScLiteral |
                             _: ScReferenceExpression |
                             _: ScParenthesisedExpr)) =>
        "!" + expression.getNode.getText
      case Array(ScPrefixExpr(operation, operand@Typeable(operandType)), _*)
        if operation.refName == "!" && operandType.conforms(api.Boolean(operand)) =>
        operand.getNode.getText
      case _ => super.getTemplateAsString(elements)
    }

    override protected def needParenthesis(element: PsiElement): Boolean = super.needParenthesis(element) &&
      //are in boolean operation expr (operation priorities are known) we don't spend time on removing parentheses
      !(element.getParent match {
        case expression: ScExpression => isBooleanOperation(expression)
        case _ => false
      })
  }

}
