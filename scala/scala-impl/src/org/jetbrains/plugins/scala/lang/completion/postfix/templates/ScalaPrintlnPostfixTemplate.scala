package org.jetbrains.plugins.scala.lang.completion.postfix.templates

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.{SelectorConditions, AncestorSelector}
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.SelectorType._

/**
 * @author Roman.Shein
 * @since 13.09.2015.
 */
class ScalaPrintlnPostfixTemplate(val alias: String = "sout") extends ScalaStringBasedPostfixTemplate(alias, "println(expr)",
  new AncestorSelector(SelectorConditions.ANY_EXPR, Topmost)) {

  override def getTemplateString(element: PsiElement): String = "println($expr$)"
}
