package org.jetbrains.plugins.scala
package lang
package completion
package filters.expression

import lexer.ScalaTokenTypes
import parser.ScalaElementTypes
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.filters.ElementFilter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.scala.lang.psi._
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.parser._
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil._

/** 
* @author Alexander Podkhalyuzin
* Date: 22.05.2008
*/

class FinallyFilter extends ElementFilter{
  def isAcceptable(element: Object, context: PsiElement): Boolean = {
    if (context.isInstanceOf[PsiComment]) return false
    val leaf = getLeafByOffset(context.getTextRange().getStartOffset(), context);
    if (leaf != null) {
      val parent = leaf.getParent()
      var i = context.getTextRange().getStartOffset() - 1
      while (i > 0 && (context.getContainingFile.getText.charAt(i) == ' ' ||
              context.getContainingFile.getText.charAt(i) == '\n')) i = i - 1
      var leaf1 = getLeafByOffset(i, context)
      while (leaf1 != null && !leaf1.isInstanceOf[ScTryStmt]) leaf1 = leaf1.getParent
      if (leaf1 == null) return false
      if (leaf1.getNode.getChildren(null).map(_.getElementType == ScalaElementTypes.FINALLY_BLOCK).contains(true)) return false
      i = context.getTextRange().getEndOffset() 
      while (i < context.getContainingFile.getText.length - 1 && (context.getContainingFile.getText.charAt(i) == ' ' ||
                 context.getContainingFile.getText.charAt(i) == '\n')) i = i + 1
      if (Array("catch", "finally").contains(getLeafByOffset(i, context).getText)) return false
      return true
    }
    return false;
  }

  def isClassAcceptable(hintClass: java.lang.Class[_]): Boolean = {
    return true;
  }

  @NonNls
  override def toString(): String = {
    return "statements keyword filter"
  }
}