package org.jetbrains.plugins.scala.lang.completion.postfix.templates

import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.AncestorSelector._

final class ScalaPrintlnPostfixTemplate(alias: String = "sout") extends ScalaStringBasedPostfixTemplate(
  alias,
  "println(expr)",
  SelectTopmostAncestors(AnyExpression)
) with DumbAware {
  override def getTemplateString(element: PsiElement): String = "println($expr$)"
}
