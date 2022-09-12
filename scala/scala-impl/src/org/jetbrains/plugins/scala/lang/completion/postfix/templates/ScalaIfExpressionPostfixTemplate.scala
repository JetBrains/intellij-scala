package org.jetbrains.plugins.scala.lang.completion.postfix.templates

import com.intellij.codeInsight.template.postfix.templates.IfPostfixTemplateBase
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.AncestorSelector.SelectTopmostAncestors
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.ScalaPostfixTemplatePsiInfo
import org.jetbrains.plugins.scala.lang.surroundWith.surrounders.expression.ScalaWithIfConditionSurrounder

final class ScalaIfExpressionPostfixTemplate extends IfPostfixTemplateBase(
  ScalaPostfixTemplatePsiInfo,
  SelectTopmostAncestors(ScalaWithIfConditionSurrounder)
) {
  override def getSurrounder: ScalaWithIfConditionSurrounder.type = ScalaWithIfConditionSurrounder
}
