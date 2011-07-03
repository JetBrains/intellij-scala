package org.jetbrains.plugins.scala
package lang
package completion
package filters.definitions

import com.intellij.psi.filters.ElementFilter
import org.jetbrains.annotations.NonNls
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil._
import org.jetbrains.plugins.scala.lang.lexer._
import org.jetbrains.plugins.scala.lang.parser._
import psi.api.ScalaFile

/** 
* @author Alexander Podkhalyuzin
* Date: 22.05.2008
*/

class DefinitionsFilter extends ElementFilter {
  def isAcceptable(element: Object, context: PsiElement): Boolean = {
    if (context.isInstanceOf[PsiComment]) return false
    val leaf = getLeafByOffset(context.getTextRange.getStartOffset, context);
    if (leaf != null) {
      val parent = leaf.getParent;
      parent match {
        case _: ScClassParameter =>
          return true
        case _: ScReferenceExpression =>
        case _ => return false
      }
      parent.getParent match {
        case parent@(_: ScBlockExpr | _: ScTemplateBody | _: ScClassParameter | _: ScalaFile)
          if !parent.isInstanceOf[ScalaFile] || parent.asInstanceOf[ScalaFile].isScriptFile() => {
          if ((leaf.getPrevSibling == null || leaf.getPrevSibling.getPrevSibling == null ||
            leaf.getPrevSibling.getPrevSibling.getNode.getElementType != ScalaTokenTypes.kDEF) &&
            (parent.getPrevSibling == null || parent.getPrevSibling.getPrevSibling == null ||
              (parent.getPrevSibling.getPrevSibling.getNode.getElementType != ScalaElementTypes.MATCH_STMT ||
                !parent.getPrevSibling.getPrevSibling.getLastChild.isInstanceOf[PsiErrorElement])))
            return true
        }
        case _ =>
      }
    }
    false
  }

  def isClassAcceptable(hintClass: java.lang.Class[_]): Boolean = {
    true
  }

  @NonNls
  override def toString: String = {
    "val, var, type keyword filter";
  }
}