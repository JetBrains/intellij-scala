package org.jetbrains.plugins.scala.lang.completion.postfix.templates

import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.AncestorSelector.SelectAllAncestors

final class ScalaSeqPostfixTemplate extends ScalaStringBasedPostfixTemplate(
  "Seq",
  "Seq(expr)",
  SelectAllAncestors()
) with DumbAware {
  override def getTemplateString(element: PsiElement): String = "Seq($expr$)"
}