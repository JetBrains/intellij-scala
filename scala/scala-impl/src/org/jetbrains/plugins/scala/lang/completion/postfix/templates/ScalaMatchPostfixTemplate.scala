package org.jetbrains.plugins.scala.lang.completion.postfix.templates

import com.intellij.codeInsight.template.postfix.templates.SurroundPostfixTemplateBase
import com.intellij.lang.surroundWith.Surrounder
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.{SelectorConditions, AncestorSelector, ScalaPostfixTemplatePsiInfo}
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.SelectorType._
import org.jetbrains.plugins.scala.lang.surroundWith.surrounders.expression.ScalaWithMatchSurrounder

/**
 * @author Roman.Shein
 * @since 09.09.2015.
 */
class ScalaMatchPostfixTemplate extends FormattingSurroundPostfixTemplate("match", "expr match {...}",
  ScalaPostfixTemplatePsiInfo, ScalaMatchPostfixTemplate.surrounder) {
}

object ScalaMatchPostfixTemplate {
  private val surrounder = new ScalaWithMatchSurrounder
}