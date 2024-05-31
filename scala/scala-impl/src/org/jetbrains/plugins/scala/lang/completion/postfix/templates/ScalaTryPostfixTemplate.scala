package org.jetbrains.plugins.scala.lang.completion.postfix.templates

import com.intellij.codeInsight.template.postfix.templates.SurroundPostfixTemplateBase
import com.intellij.openapi.project.DumbAware
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.AncestorSelector.SelectTopmostAncestors
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.ScalaPostfixTemplatePsiInfo
import org.jetbrains.plugins.scala.lang.surroundWith.surrounders.expression.ScalaWithTryCatchSurrounder

final class ScalaTryPostfixTemplate extends SurroundPostfixTemplateBase(
  "try",
  "try { exp } catch {}",
  ScalaPostfixTemplatePsiInfo,
  SelectTopmostAncestors(ScalaWithTryCatchSurrounder),
  null
) with DumbAware {
  override def getSurrounder: ScalaWithTryCatchSurrounder.type = ScalaWithTryCatchSurrounder
}
