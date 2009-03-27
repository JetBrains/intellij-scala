package org.jetbrains.plugins.scala.lang.completion.filters.definitions

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.filters.ElementFilter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.scala.lang.psi._
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil._
import org.jetbrains.plugins.scala.lang.lexer._
import org.jetbrains.plugins.scala.lang.parser._

/** 
* @author Alexander Podkhalyuzin
* Date: 28.05.2008
*/

class DefTypeFilter extends ElementFilter {
  def isAcceptable(element: Object, context: PsiElement): Boolean = {
    if (context.isInstanceOf[PsiComment]) return false
    val leaf = getLeafByOffset(context.getTextRange().getStartOffset(), context);
    if (leaf != null) {
      val parent = leaf.getParent();
      parent match {
        case _: ScReferenceExpression =>
        case _ => return false
      }
      parent.getParent match {
        case _: ScBlockExpr | _: ScTemplateBody | _: ScClassParameter => {
          if ((leaf.getPrevSibling == null || leaf.getPrevSibling.getPrevSibling == null ||
                  leaf.getPrevSibling.getPrevSibling.getNode.getElementType != ScalaTokenTypes.kDEF) && (parent.getPrevSibling == null ||
                  parent.getPrevSibling.getPrevSibling == null ||
                  (parent.getPrevSibling.getPrevSibling.getNode.getElementType != ScalaElementTypes.MATCH_STMT || !parent.getPrevSibling.getPrevSibling.getLastChild.isInstanceOf[PsiErrorElement])))
            return true
        }
        case _ =>
      }
    }
    return false
  }

  def isClassAcceptable(hintClass: java.lang.Class[_]): Boolean = {
    return true
  }

  @NonNls
  override def toString(): String = {
    return "'def' keyword filter";
  }
}