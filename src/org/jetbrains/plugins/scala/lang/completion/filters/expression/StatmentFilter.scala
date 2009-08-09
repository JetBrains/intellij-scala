package org.jetbrains.plugins.scala
package lang
package completion
package filters.expression

import psi.api.base.patterns.ScStableReferenceElementPattern
import psi.api.toplevel.templates.ScTemplateBody
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

class StatementFilter extends ElementFilter {
  def isAcceptable(element: Object, context: PsiElement): Boolean = {
    if (context.isInstanceOf[PsiComment]) return false
    val leaf = getLeafByOffset(context.getTextRange().getStartOffset(), context);
    if (leaf != null) {
      val parent = leaf.getParent();
      if (parent.isInstanceOf[ScReferenceExpression] &&
              !parent.getParent.isInstanceOf[ScStableReferenceElementPattern] &&
              (!parent.getParent.isInstanceOf[ScInfixExpr]) && (parent.getPrevSibling == null ||
              parent.getPrevSibling.getPrevSibling == null ||
              (parent.getPrevSibling.getPrevSibling.getNode.getElementType != ScalaElementTypes.MATCH_STMT ||
                      !parent.getPrevSibling.getPrevSibling.getLastChild.isInstanceOf[PsiErrorElement]))) {
        parent.getParent match {
          case _: ScBlockExpr | _: ScBlock | _: ScTemplateBody => return true
          case x: ScExpression => return checkReplace(x, "if (true) true", x.getManager)
          case _ =>
        }
        return true
      }
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