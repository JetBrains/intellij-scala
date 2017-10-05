package org.jetbrains.plugins.scala.lang.completion.postfix.templates

import com.intellij.codeInsight.template.postfix.templates.SurroundPostfixTemplateBase
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.SelectorType._
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.{SelectorConditions, AncestorSelector, ScalaPostfixTemplatePsiInfo}
import org.jetbrains.plugins.scala.lang.surroundWith.surrounders.expression.ScalaWithTryCatchSurrounder

/**
 * @author Roman.Shein
 * @since 05.09.2015.
 */
class ScalaTryPostfixTemplate extends SurroundPostfixTemplateBase("try", "try { exp } catch {}",
  ScalaPostfixTemplatePsiInfo, AncestorSelector(ScalaTryPostfixTemplate.surrounder, Topmost)) {

  override def getSurrounder: ScalaWithTryCatchSurrounder = ScalaTryPostfixTemplate.surrounder
}

object ScalaTryPostfixTemplate {
  private val surrounder = new ScalaWithTryCatchSurrounder
}