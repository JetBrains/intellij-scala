package org.jetbrains.plugins.scala
package lang
package completion
package filters.expression

import com.intellij.psi.filters.ElementFilter
import com.intellij.psi.{PsiElement, _}
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.scala.extensions.PsiFileExt
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil._
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocComment

/** 
* @author Alexander Podkhalyuzin
* Date: 22.05.2008
*/

class FinallyFilter extends ElementFilter{
  def isAcceptable(element: Object, context: PsiElement): Boolean = {
    if (context.isInstanceOf[PsiComment]) return false
    val leaf = getLeafByOffset(context.getTextRange.getStartOffset, context)
    if (leaf != null) {
      var i = getPrevNotWhitespaceAndComment(context.getTextRange.getStartOffset - 1, context)
      var leaf1 = getLeafByOffset(i, context)
      while (leaf1 != null && !leaf1.isInstanceOf[ScTryStmt]) leaf1 = leaf1.getParent
      if (leaf1 == null) return false
      if (leaf1.getNode.getChildren(null).exists(_.getElementType == ScalaElementTypes.FINALLY_BLOCK)) return false
      i = getNextNotWhitespaceAndComment(context.getTextRange.getEndOffset, context)
      if (Array("catch", "finally").contains(getLeafByOffset(i, context).getText)) return false
      return true
    }
    false
  }

  def isClassAcceptable(hintClass: java.lang.Class[_]): Boolean = {
    true
  }

  @NonNls
  override def toString: String = {
    "statements keyword filter"
  }
}
