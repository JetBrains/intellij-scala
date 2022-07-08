package org.jetbrains.plugins.scala.lang
package completion
package postfix
package templates

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.AncestorSelector._

final class ScalaReturnPostfixTemplate extends ScalaStringBasedPostfixTemplate(
  "return",
  "return expr",
  SelectTopmostAncestors(AnyExpression)
) {
  override def getTemplateString(element: PsiElement): String = "return $expr$"
}
