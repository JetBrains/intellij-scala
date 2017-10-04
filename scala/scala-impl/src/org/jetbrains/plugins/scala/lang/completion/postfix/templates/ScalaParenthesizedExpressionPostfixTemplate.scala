package org.jetbrains.plugins.scala.lang.completion.postfix.templates

import com.intellij.codeInsight.template.postfix.templates.ParenthesizedPostfixTemplate
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.{SelectorConditions, AncestorSelector, ScalaPostfixTemplatePsiInfo}
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.SelectorType._


/**
 * @author Roman.Shein
 * @since 10.09.2015.
 */
class ScalaParenthesizedExpressionPostfixTemplate extends ParenthesizedPostfixTemplate(ScalaPostfixTemplatePsiInfo,
  new AncestorSelector(SelectorConditions.ANY_EXPR, All)) {

}
