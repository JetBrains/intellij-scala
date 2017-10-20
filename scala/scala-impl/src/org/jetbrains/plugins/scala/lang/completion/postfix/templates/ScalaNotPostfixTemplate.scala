package org.jetbrains.plugins.scala.lang.completion.postfix.templates

import com.intellij.codeInsight.template.postfix.templates.NotPostfixTemplate
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.{SelectorConditions, AncestorSelector, ScalaPostfixTemplatePsiInfo}
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.SelectorType._

/**
 * @author Roman.Shein
 * @since 11.09.2015.
 */
class ScalaNotPostfixTemplate(val alias: String = "not") extends NotPostfixTemplate(alias, "." + alias, "!expr",
  ScalaPostfixTemplatePsiInfo, new AncestorSelector(SelectorConditions.BOOLEAN_EXPR, All)) {
}
