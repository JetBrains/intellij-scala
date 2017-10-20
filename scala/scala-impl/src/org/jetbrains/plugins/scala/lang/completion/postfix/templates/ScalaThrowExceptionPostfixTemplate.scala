package org.jetbrains.plugins.scala.lang.completion.postfix.templates

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.{SelectorConditions, AncestorSelector}
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.SelectorType._

/**
 * @author Roman.Shein
 * @since 14.09.2015.
 */
class ScalaThrowExceptionPostfixTemplate extends ScalaStringBasedPostfixTemplate("throw", "throw expr",
  new AncestorSelector(SelectorConditions.THROWABLE, Topmost)) {

  override def getTemplateString(element: PsiElement): String = "throw $expr$"
}
