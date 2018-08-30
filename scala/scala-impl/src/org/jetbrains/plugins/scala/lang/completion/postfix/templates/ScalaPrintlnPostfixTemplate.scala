package org.jetbrains.plugins.scala.lang
package completion
package postfix
package templates

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.AncestorSelector._

/**
 * @author Roman.Shein
 * @since 13.09.2015.
 */
final class ScalaPrintlnPostfixTemplate(alias: String = "sout") extends ScalaStringBasedPostfixTemplate(
  alias,
  "println(expr)",
  SelectTopmostAncestors(AnyExpression)
) {
  override def getTemplateString(element: PsiElement): String = "println($expr$)"
}
