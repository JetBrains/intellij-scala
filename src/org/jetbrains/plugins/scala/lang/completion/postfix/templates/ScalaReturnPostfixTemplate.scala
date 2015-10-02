package org.jetbrains.plugins.scala.lang.completion.postfix.templates

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.{SelectorConditions, AncestorSelector}
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.SelectorType._

/**
 * @author Roman.Shein
 * @since 11.09.2015.
 */
class ScalaReturnPostfixTemplate extends ScalaStringBasedPostfixTemplate("return", "return expr",
  new AncestorSelector(SelectorConditions.ANY_EXPR, Topmost)) {

  override def getTemplateString(element: PsiElement): String = "return $expr$"
}
