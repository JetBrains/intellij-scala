package org.jetbrains.plugins.scala.lang.completion.postfix.templates

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.{AncestorSelector, SelectorConditions}
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.SelectorType._

/**
 * @author Roman.Shein
 * @since 09.09.2015.
 */
class ScalaForEachPostfixTemplate extends ScalaStringBasedPostfixTemplate("for", "for (elem: collection) {...}",
  new AncestorSelector(SelectorConditions.isDescendantCondition("scala.collection.GenTraversableOnce") ||
          SelectorConditions.isDescendantCondition("scala.Array"), Topmost)) {

  override def getTemplateString(element: PsiElement): String = "for (elem <- $expr$) {$END$}"
}
