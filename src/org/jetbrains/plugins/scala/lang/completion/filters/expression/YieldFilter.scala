package org.jetbrains.plugins.scala
package lang
package completion
package filters.expression

import lexer.ScalaTokenTypes
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
* Date: 28.05.2008
*/

class YieldFilter extends ElementFilter {
  def isAcceptable(element: Object, context: PsiElement): Boolean = {
    if (context.isInstanceOf[PsiComment]) return false
    val leaf = getLeafByOffset(context.getTextRange().getStartOffset(), context);
    if (leaf != null) {
      val parent = leaf.getParent()
      if (parent.isInstanceOf[ScExpression] && parent.getParent().isInstanceOf[ScForStatement]) {
        var i = context.getTextRange().getStartOffset() - 1
        while (i > 0 && (context.getContainingFile.getText.charAt(i) == ' ' ||
                 context.getContainingFile.getText.charAt(i) == '\n')) i = i - 1
        if (getLeafByOffset(i, context).getText == "yield") return false
        i = context.getTextRange.getEndOffset()
        while (i < context.getContainingFile.getText.length - 1 && (context.getContainingFile.getText.charAt(i) == ' ' ||
                 context.getContainingFile.getText.charAt(i) == '\n')) i = i + 1
        if (getLeafByOffset(i, context).getText == "yield") return false
        for (child <- parent.getParent.getNode.getChildren(null) if child.getElementType == ScalaTokenTypes.kYIELD) return false
        return ScalaCompletionUtil.checkAnyWith(parent.getParent, "yield true", context.getManager) ||
          ScalaCompletionUtil.checkReplace(parent.getParent, "yield", context.getManager)
      }
    }
    return false;
  }

  def isClassAcceptable(hintClass: java.lang.Class[_]): Boolean = {
    return true;
  }

  @NonNls
  override def toString(): String = {
    return "'yield' keyword filter"
  }
}