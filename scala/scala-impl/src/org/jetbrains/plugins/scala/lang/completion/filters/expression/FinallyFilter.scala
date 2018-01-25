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

  def getPrevNotWhitespaceAndComment(index: Int, context: PsiElement): Int = {
    if (index < 0) return 0
    val file = context.getContainingFile
    val fileText = file.charSequence
    var i = index
    while (i > 0 && fileText.charAt(i).isWhitespace) {
      i = i - 1
    }
    val leaf = getLeafByOffset(i, context)
    if (leaf.isInstanceOf[PsiComment] || leaf.isInstanceOf[ScDocComment])
      return getPrevNotWhitespaceAndComment(leaf.getTextRange.getStartOffset - 1, context)
    i
  }

  def getNextNotWhitespaceAndComment(index: Int, context: PsiElement): Int = {
    val file = context.getContainingFile
    if (index >= file.getTextLength - 1) return file.getTextLength - 2

    val fileText = file.charSequence
    var i = index
    while (i < fileText.length - 1 && fileText.charAt(i).isWhitespace) {
      i = i + 1
    }
    val leaf = getLeafByOffset(i, context)
    if (leaf.isInstanceOf[PsiComment] || leaf.isInstanceOf[ScDocComment])
      return getNextNotWhitespaceAndComment(leaf.getTextRange.getEndOffset, context)
    i
  }
}
