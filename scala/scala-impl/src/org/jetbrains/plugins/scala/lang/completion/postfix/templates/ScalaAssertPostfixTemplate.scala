package org.jetbrains.plugins.scala.lang
package completion
package postfix
package templates

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.AncestorSelector.SelectTopmostAncestors

final class ScalaAssertPostfixTemplate extends ScalaStringBasedPostfixTemplate(
  "assert",
  "assert(expr)",
  SelectTopmostAncestors()
) {
  override def getTemplateString(element: PsiElement): String = "assert($expr$)"
}
