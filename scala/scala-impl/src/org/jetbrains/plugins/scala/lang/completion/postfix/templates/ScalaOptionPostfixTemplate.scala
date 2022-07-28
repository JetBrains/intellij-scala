package org.jetbrains.plugins.scala.lang
package completion
package postfix
package templates

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.AncestorSelector.SelectAllAncestors

final class ScalaOptionPostfixTemplate extends ScalaStringBasedPostfixTemplate(
  "Option",
  "Option(expr)",
  SelectAllAncestors()
) {
  override def getTemplateString(element: PsiElement): String = "Option($expr$)"
}
