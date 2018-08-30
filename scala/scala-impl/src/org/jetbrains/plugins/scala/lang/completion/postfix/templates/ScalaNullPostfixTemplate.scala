package org.jetbrains.plugins.scala.lang
package completion
package postfix
package templates

import com.intellij.codeInsight.template.postfix.templates.SurroundPostfixTemplateBase
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.AncestorSelector.SelectTopmostAncestors
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.ScalaPostfixTemplatePsiInfo
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScInfixExpr
import org.jetbrains.plugins.scala.lang.surroundWith.surrounders.expression.ScalaWithIfConditionSurrounder

/**
 * @author Roman.Shein
 * @since 14.09.2015.
 */
sealed abstract class ScalaNullPostfixTemplate(name: String, character: Char) extends SurroundPostfixTemplateBase(
  name,
  s"if (expr $character= null) {}",
  ScalaPostfixTemplatePsiInfo,
  SelectTopmostAncestors(ScalaWithIfConditionSurrounder)
) {

  override protected def getWrappedExpression(expression: PsiElement): PsiElement = {
    val (prefix, suffix) = expression match {
      case _: ScInfixExpr => ("(", ")")
      case _ => ("", "")
    }
    myPsiInfo.createExpression(expression, prefix + getHead, suffix + getTail)
  }

  override final def getTail: String = character + "= null"

  override final def getSurrounder: ScalaWithIfConditionSurrounder.type = ScalaWithIfConditionSurrounder
}

final class ScalaNotNullPostfixTemplate(alias: String = "notnull") extends ScalaNullPostfixTemplate(alias, '!')

final class ScalaIsNullPostfixTemplate extends ScalaNullPostfixTemplate("null", '=')