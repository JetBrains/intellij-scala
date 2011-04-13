package org.jetbrains.plugins.scala
package lang
package completion
package filters.expression

import com.intellij.psi.filters.ElementFilter;
import org.jetbrains.annotations.NonNls;
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.parser._
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil._
import lexer.ScalaTokenTypes

/** 
* @author Alexander Podkhalyuzin
* Date: 28.05.2008
*/

class IfFilter extends ElementFilter {
  def isAcceptable(element: Object, context: PsiElement): Boolean = {
    if (context.isInstanceOf[PsiComment]) return false
    val leaf = getLeafByOffset(context.getTextRange.getStartOffset, context);
    if (leaf != null) {
      var parent = leaf.getParent
      while (parent != null) {
        if (parent.getNode.getElementType == ScalaElementTypes.CASE_CLAUSE ||
                parent.getNode.getElementType == ScalaElementTypes.FOR_STMT) {
          import extensions._
          if (leaf.getParent != null && //reference
              leaf.getParent.getParent != null &&  //pattern
              leaf.getParent.getParent.getPrevSiblingNotWhitespace != null && //case keyword
              leaf.getParent.getParent.getPrevSiblingNotWhitespace.
                getNode.getElementType == ScalaTokenTypes.kCASE) return false
          return true
        }
        parent = parent.getParent
      }
    }
    return false;
  }

  def isClassAcceptable(hintClass: java.lang.Class[_]): Boolean = {
    return true;
  }

  @NonNls
  override def toString: String = {
    return "'if' keyword filter"
  }
}