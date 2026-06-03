package org.jetbrains.plugins.scala.lang.completion.postfix.templates

import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.AncestorSelector._

final class ScalaReturnPostfixTemplate extends ScalaStringBasedPostfixTemplate(
  "return",
  "return expr",
  SelectTopmostAncestors(AnyExpression)
) with DumbAware {
  override def getTemplateString(element: PsiElement): String = "return $expr$"
}
