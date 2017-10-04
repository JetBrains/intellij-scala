package org.jetbrains.plugins.scala.lang.completion.postfix.templates

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.{SelectorConditions, AncestorSelector}
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.SelectorType._

/**
  * @author Roman.Shein
  *         Date: 24.12.2015
  */
class ScalaOptionPostfixTemplate extends ScalaStringBasedPostfixTemplate("Option", "Option(expr)",
  new AncestorSelector(SelectorConditions.ANY_EXPR, All)) {
  override def getTemplateString(element: PsiElement): String = "Option($expr$)"
}
