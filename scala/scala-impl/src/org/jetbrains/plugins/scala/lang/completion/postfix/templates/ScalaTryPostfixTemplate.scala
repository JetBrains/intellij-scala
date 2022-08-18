package org.jetbrains.plugins.scala.lang
package completion
package postfix
package templates

import com.intellij.codeInsight.template.postfix.templates.SurroundPostfixTemplateBase
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.AncestorSelector.SelectTopmostAncestors
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.ScalaPostfixTemplatePsiInfo
import org.jetbrains.plugins.scala.lang.surroundWith.surrounders.expression.ScalaWithTryCatchSurrounder

import scala.annotation.nowarn

/**
 * @author Roman.Shein
 * @since 05.09.2015.
 */
@nowarn("cat=deprecation")
final class ScalaTryPostfixTemplate extends SurroundPostfixTemplateBase(
  "try",
  "try { exp } catch {}",
  ScalaPostfixTemplatePsiInfo,
  SelectTopmostAncestors(ScalaWithTryCatchSurrounder)
) {
  override def getSurrounder: ScalaWithTryCatchSurrounder.type = ScalaWithTryCatchSurrounder
}