package org.jetbrains.plugins.scala.lang.completion.postfix.templates

import com.intellij.codeInsight.template.postfix.templates.{PostfixTemplateExpressionSelectorBase, StringBasedPostfixTemplate}
import com.intellij.psi.PsiElement

abstract class ScalaStringBasedPostfixTemplate(name: String,
                                               example: String,
                                               selector: PostfixTemplateExpressionSelectorBase)
  extends StringBasedPostfixTemplate(name, example, selector, null) {

  override def getElementToRemove(expression: PsiElement): PsiElement = expression
}
