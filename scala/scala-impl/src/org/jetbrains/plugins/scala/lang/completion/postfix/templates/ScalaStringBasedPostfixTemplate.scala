package org.jetbrains.plugins.scala.lang
package completion
package postfix
package templates

import com.intellij.codeInsight.template.postfix.templates.{PostfixTemplateExpressionSelectorBase, StringBasedPostfixTemplate}
import com.intellij.psi.PsiElement

/**
  * @author Roman.Shein
  * @since 14.09.2015.
  */
abstract class ScalaStringBasedPostfixTemplate(name: String,
                                               example: String,
                                               selector: PostfixTemplateExpressionSelectorBase)
  extends StringBasedPostfixTemplate(name, example, selector, null) {

  override def getElementToRemove(expression: PsiElement): PsiElement = expression
}
