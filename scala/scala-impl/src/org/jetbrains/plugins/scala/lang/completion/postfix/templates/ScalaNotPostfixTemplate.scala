package org.jetbrains.plugins.scala.lang
package completion
package postfix
package templates

import com.intellij.codeInsight.template.postfix.templates.NotPostfixTemplate
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.AncestorSelector._
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.ScalaPostfixTemplatePsiInfo

final class ScalaNotPostfixTemplate(alias: String = "not", needsDotInKey: Boolean = true) extends NotPostfixTemplate(
  alias,
  if (needsDotInKey) "." + alias else alias,
  "!expr",
  ScalaPostfixTemplatePsiInfo,
  SelectAllAncestors(BooleanExpression)
)
