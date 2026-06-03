package org.jetbrains.plugins.scala.lang.completion.postfix.templates

import com.intellij.codeInsight.template.postfix.templates.ParenthesizedPostfixTemplate
import com.intellij.openapi.project.DumbAware
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.AncestorSelector.SelectAllAncestors
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.ScalaPostfixTemplatePsiInfo

final class ScalaParenthesizedExpressionPostfixTemplate extends ParenthesizedPostfixTemplate(
  ScalaPostfixTemplatePsiInfo,
  SelectAllAncestors(),
  null
) with DumbAware
