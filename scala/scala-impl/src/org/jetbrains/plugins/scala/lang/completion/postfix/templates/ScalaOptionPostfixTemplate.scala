package org.jetbrains.plugins.scala.lang.completion.postfix.templates

import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.AncestorSelector.SelectAllAncestors

final class ScalaOptionPostfixTemplate extends ScalaStringBasedPostfixTemplate(
  "Option",
  "Option(expr)",
  SelectAllAncestors()
) with DumbAware {
  override def getTemplateString(element: PsiElement): String = "Option($expr$)"
}
