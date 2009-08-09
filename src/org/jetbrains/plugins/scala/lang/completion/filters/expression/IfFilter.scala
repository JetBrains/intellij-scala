package org.jetbrains.plugins.scala
package lang
package completion
package filters.expression

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.filters.ElementFilter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.scala.lang.psi._
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._
import org.jetbrains.plugins.scala.lang.parser._
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._

/** 
* @author Alexander Podkhalyuzin
* Date: 28.05.2008
*/

//now it means nothing, never used
class IfFilter extends ElementFilter {
  def isAcceptable(element: Object, context: PsiElement): Boolean = {
    if (context.isInstanceOf[PsiComment]) return false
    val leaf = getLeafByOffset(context.getTextRange().getStartOffset(), context);
    if (leaf != null) {
      var parent = leaf.getParent()
      while (parent != null) {
        if (parent.getNode.getElementType == ScalaElementTypes.CASE_CLAUSE ||
                parent.getNode.getElementType == ScalaElementTypes.FOR_STMT) return true
        parent = parent.getParent
      }
    }
    return false;
  }

  def isClassAcceptable(hintClass: java.lang.Class[_]): Boolean = {
    return true;
  }

  @NonNls
  override def toString(): String = {
    return "'if' keyword filter"
  }
}