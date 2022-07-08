package org.jetbrains.plugins.scala.lang
package completion
package postfix
package templates

import com.intellij.codeInsight.template.postfix.templates.ParenthesizedPostfixTemplate
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.AncestorSelector.SelectAllAncestors
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.ScalaPostfixTemplatePsiInfo

final class ScalaParenthesizedExpressionPostfixTemplate extends ParenthesizedPostfixTemplate(
  ScalaPostfixTemplatePsiInfo,
  SelectAllAncestors()
)
