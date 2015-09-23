package org.jetbrains.plugins.scala.lang.completion.postfix.templates

import com.intellij.codeInsight.template.postfix.templates.SurroundPostfixTemplateBase
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.{SelectorConditions, AncestorSelector, ScalaPostfixTemplatePsiInfo}
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.SelectorType._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScInfixExpr
import org.jetbrains.plugins.scala.lang.surroundWith.surrounders.expression.ScalaWithIfConditionSurrounder

/**
 * @author Roman.Shein
 * @since 14.09.2015.
 */
abstract class ScalaNullPostfixTemplate(val name: String, val example: String) extends SurroundPostfixTemplateBase(name,
  example, new ScalaPostfixTemplatePsiInfo, new AncestorSelector(SelectorConditions.ANY_EXPR, Topmost)){

  override protected def getWrappedExpression(expression: PsiElement): PsiElement = {
    val (head, tail) = expression match {
      case prefix: ScInfixExpr => ("(" + getHead, ")" + getTail)
      case _ => (getHead, getTail)
    }
    myPsiInfo.createExpression(expression, head, tail)
  }

  override def getSurrounder = new ScalaWithIfConditionSurrounder

}
