package org.jetbrains.plugins.scala.lang
package completion
package postfix
package templates

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.AncestorSelector.SelectTopmostAncestors

final class ScalaDoWhilePostfixTemplate extends ScalaStringBasedPostfixTemplate(
  "dowhile",
  "do {} while (expr)",
  SelectTopmostAncestors()
) {
  override def getTemplateString(element: PsiElement): String = "do {\n$END$\n} while ($expr$)"
}
