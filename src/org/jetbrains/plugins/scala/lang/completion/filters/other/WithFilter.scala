package org.jetbrains.plugins.scala
package lang
package completion
package filters.other

import com.intellij.psi.{PsiElement, _}
import com.intellij.psi.filters.ElementFilter
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._

/** 
* @author Alexander Podkhalyuzin
* Date: 28.05.2008
*/

class WithFilter extends ElementFilter {
  def isAcceptable(element: Object, context: PsiElement): Boolean = {
    if (context.isInstanceOf[PsiComment]) return false
    val leaf = getLeafByOffset(context.getTextRange().getStartOffset(), context)
    if (leaf != null) {
      var i = context.getTextRange().getStartOffset() - 1
      while (i >= 0 && context.getContainingFile.getText.charAt(i) == ' ') i = i - 1
      if (i >= 0) {
        var leaf1 = getLeafByOffset(i, context)
        while (leaf1 != null &&
                !leaf1.isInstanceOf[ScTypeDefinition]) {
          leaf1 = leaf1.getParent
        }
        if (leaf1 != null && leaf1.getTextRange.getEndOffset != i+1 && leaf1.getTextRange.getEndOffset != leaf.getTextRange.getEndOffset &&
          leaf1.getTextRange.getEndOffset != leaf.getTextRange.getStartOffset) leaf1 = null
        leaf1 match {
          case null =>
          case x: ScTypeDefinition => {
            return checkClassWith(x, "with A", x.getManager)
          }
        }
        leaf1 = getLeafByOffset(i, context)
        while (leaf1 != null && !leaf1.isInstanceOf[ScTypeElement] &&
                !leaf1.isInstanceOf[ScNewTemplateDefinition]) {
          leaf1 = leaf1.getParent
        }
        if (leaf1 != null && leaf1.getTextRange.getEndOffset != i+1 && leaf1.getTextRange.getEndOffset != leaf.getTextRange.getEndOffset &&
          leaf1.getTextRange.getEndOffset != leaf.getTextRange.getStartOffset) leaf1 = null
        leaf1 match {
          case null => return false
          case x: ScTypeElement => {
            return checkTypeWith(x, "with A", x.getManager)
          }
          case x: ScNewTemplateDefinition => {
            return checkNewWith(x, "with A", x.getManager)
          }
        }
      } 
    }
    return false
  }

  def isClassAcceptable(hintClass: java.lang.Class[_]): Boolean = {
    return true;
  }

  @NonNls
  override def toString(): String = {
    return "'with' keyword filter"
  }
}