package org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplatePsiInfo
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.booleans.{SimplifyBooleanUtil, DoubleNegationUtil}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScReferenceExpression, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.surroundWith.surrounders.expression.ScalaWithUnaryNotSurrounder

/**
 * @author Roman.Shein
 * @since 09.09.2015.
 */
class ScalaPostfixTemplatePsiInfo extends PostfixTemplatePsiInfo {

  private val notSurrounder = new ScalaWithUnaryNotSurrounder() {
    override def getTemplateAsString(elements: Array[PsiElement]) = if (elements.length == 1) {
      elements(0) match {
        case literal: ScLiteral if literal.getText == "true" => "false"
        case literal: ScLiteral if literal.getText == "false" => "true"
        case id: ScReferenceExpression if id.getNode.getChildren(null).length == 1 &&
                id.getNode.getChildren(null).apply(0).getElementType == ScalaTokenTypes.tIDENTIFIER => "!" + id.getText
        case _ => super.getTemplateAsString(elements)
      }
    } else {
      super.getTemplateAsString(elements)
    }
  }

  override def getNegatedExpression(element: PsiElement): PsiElement =
    element match {
      case expr if notSurrounder.isApplicable(Array(expr)) =>
        var res = notSurrounder.surroundPsi(Array(element)).asInstanceOf[ScExpression]
        if (DoubleNegationUtil.hasDoubleNegation(res)) {
          res = DoubleNegationUtil.removeDoubleNegation(res)
        }
        if (SimplifyBooleanUtil.canBeSimplified(res)) {
          res = SimplifyBooleanUtil.simplify(res)
        }
        res
      case _ => throw new IllegalArgumentException("Attempted adding negation through template for element " + element.getText
              + " which is not a valid boolean expression.")
    }

  override def createExpression(context: PsiElement, prefix: String, suffix: String): PsiElement =
    ScalaPsiElementFactory.createExpressionFromText(prefix + context.getText + suffix, context)
}
