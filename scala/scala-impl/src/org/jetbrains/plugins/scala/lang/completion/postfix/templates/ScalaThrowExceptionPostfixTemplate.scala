package org.jetbrains.plugins.scala.lang
package completion
package postfix
package templates

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.AncestorSelector._

final class ScalaThrowExceptionPostfixTemplate extends ScalaStringBasedPostfixTemplate(
  "throw",
  "throw expr",
  SelectTopmostAncestors(isSameOrInheritor("java.lang.Throwable"))
) {
  override def getTemplateString(element: PsiElement): String = "throw $expr$"
}
