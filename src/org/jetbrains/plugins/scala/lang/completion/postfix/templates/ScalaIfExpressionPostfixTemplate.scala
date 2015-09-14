package org.jetbrains.plugins.scala.lang.completion.postfix.templates

import com.intellij.codeInsight.template.postfix.templates.IfPostfixTemplateBase
import com.intellij.lang.surroundWith.Surrounder
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.{SelectorConditions, AncestorSelector, ScalaPostfixTemplatePsiInfo}
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.SelectorType._
import org.jetbrains.plugins.scala.lang.surroundWith.surrounders.expression.ScalaWithIfConditionSurrounder

/**
 * @author Roman.Shein
 * @since 09.09.2015.
 */
class ScalaIfExpressionPostfixTemplate extends IfPostfixTemplateBase(new ScalaPostfixTemplatePsiInfo,
  new AncestorSelector(SelectorConditions.BOOLEAN_EXPR, Topmost)) {
  override def getSurrounder: Surrounder = new ScalaWithIfConditionSurrounder
}
