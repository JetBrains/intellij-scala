package org.jetbrains.plugins.scala.lang
package completion
package postfix
package templates

import com.intellij.codeInsight.template.postfix.templates.IfPostfixTemplateBase
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.{AncestorSelector, ScalaPostfixTemplatePsiInfo, SelectorType}
import org.jetbrains.plugins.scala.lang.surroundWith.surrounders.expression.ScalaWithIfConditionSurrounder

/**
 * @author Roman.Shein
 * @since 09.09.2015.
 */
final class ScalaIfExpressionPostfixTemplate extends IfPostfixTemplateBase(
  ScalaPostfixTemplatePsiInfo,
  AncestorSelector(ScalaWithIfConditionSurrounder, SelectorType.Topmost)
) {
  override def getSurrounder: ScalaWithIfConditionSurrounder.type = ScalaWithIfConditionSurrounder
}
