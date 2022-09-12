package org.jetbrains.plugins.scala.lang.completion.postfix.templates

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.AncestorSelector._

final class ScalaPrintlnPostfixTemplate(alias: String = "sout") extends ScalaStringBasedPostfixTemplate(
  alias,
  "println(expr)",
  SelectTopmostAncestors(AnyExpression)
) {
  override def getTemplateString(element: PsiElement): String = "println($expr$)"
}
