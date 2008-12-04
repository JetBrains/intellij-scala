package org.jetbrains.plugins.scala.lang.completion.filters.modifiers

import annotations.NonNls
import com.intellij.psi.filters.ElementFilter
import com.intellij.psi.{PsiComment, PsiElement}
import lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil._
import psi.api.statements.params.ScParameter

/**
 *  User: Alexander Podkhalyuzin
 *  Date: 27.09.2008
 */

class ImplicitFilter extends ElementFilter {
  def isAcceptable(element: Object, context: PsiElement): Boolean = {
    if (context.isInstanceOf[PsiComment]) return false
    val leaf = getLeafByOffset(context.getTextRange().getStartOffset(), context);
    if (leaf != null) {
      val parent = leaf.getParent
      parent match {
        case _: ScParameter => {
          val prev = parent.getPrevSibling
          if (prev != null && prev.getNode != null &&
                  prev.getNode.getElementType == ScalaTokenTypes.tLPARENTHESIS) return true
        }
        case _ =>
      }
    }
    return false
  }

  def isClassAcceptable(hintClass: java.lang.Class[_]): Boolean = true

  @NonNls
  override def toString(): String = {
    return "'implicit' keyword filter";
  }
}