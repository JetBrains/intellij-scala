package org.jetbrains.plugins.scala.lang
package completion
package postfix
package templates

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.AncestorSelector._

final class ScalaForEachPostfixTemplate extends ScalaStringBasedPostfixTemplate(
  "for",
  "for (elem: collection) {...}",
  SelectTopmostAncestors(isSameOrInheritor("scala.collection.GenTraversableOnce", "scala.collection.IterableOnceOps", "scala.Array"))
) {
  override def getTemplateString(element: PsiElement): String = "for (elem <- $expr$) {$END$}"
}
