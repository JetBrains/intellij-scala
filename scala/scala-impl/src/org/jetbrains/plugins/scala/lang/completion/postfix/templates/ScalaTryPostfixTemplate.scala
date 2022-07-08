package org.jetbrains.plugins.scala.lang
package completion
package postfix
package templates

import com.intellij.codeInsight.template.postfix.templates.SurroundPostfixTemplateBase
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.AncestorSelector.SelectTopmostAncestors
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.ScalaPostfixTemplatePsiInfo
import org.jetbrains.plugins.scala.lang.surroundWith.surrounders.expression.ScalaWithTryCatchSurrounder

final class ScalaTryPostfixTemplate extends SurroundPostfixTemplateBase(
  "try",
  "try { exp } catch {}",
  ScalaPostfixTemplatePsiInfo,
  SelectTopmostAncestors(ScalaWithTryCatchSurrounder),
  null
) {
  override def getSurrounder: ScalaWithTryCatchSurrounder.type = ScalaWithTryCatchSurrounder
}
