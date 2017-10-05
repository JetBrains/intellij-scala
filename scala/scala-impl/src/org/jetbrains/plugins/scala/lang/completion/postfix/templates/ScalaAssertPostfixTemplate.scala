package org.jetbrains.plugins.scala.lang.completion.postfix.templates

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.SelectorType._
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.{SelectorConditions, AncestorSelector}

/**
 * @author Roman.Shein
 * @since 08.09.2015.
 */
class ScalaAssertPostfixTemplate extends ScalaStringBasedPostfixTemplate("assert", "assert(expr)",
  new AncestorSelector(SelectorConditions.BOOLEAN_EXPR, Topmost)) {

  override def getTemplateString(element: PsiElement): String = "assert($expr$)"
}
