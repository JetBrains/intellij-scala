package org.jetbrains.plugins.scala.lang
package completion
package postfix
package templates

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.AncestorSelector.SelectTopmostAncestors

/**
 * @author Roman.Shein
 * @since 08.09.2015.
 */
final class ScalaAssertPostfixTemplate extends ScalaStringBasedPostfixTemplate(
  "assert",
  "assert(expr)",
  SelectTopmostAncestors()
) {
  override def getTemplateString(element: PsiElement): String = "assert($expr$)"
}
