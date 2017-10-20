package org.jetbrains.plugins.scala
package lang
package completion
package filters.modifiers

import com.intellij.psi._
import com.intellij.psi.filters.ElementFilter
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil._
import org.jetbrains.plugins.scala.lang.lexer._
import org.jetbrains.plugins.scala.lang.parser._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._

/** 
* @author Alexander Podkhalyuzin
* Date: 22.05.2008
*/

class CaseFilter extends ElementFilter {
  def isAcceptable(element: Object, context: PsiElement): Boolean = {
    if (context.isInstanceOf[PsiComment]) return false
    val (leaf, _) = processPsiLeafForFilter(getLeafByOffset(context.getTextRange.getStartOffset, context))
    
    if (leaf != null && leaf.getParent != null) {
      val parent = leaf.getParent
      parent match {
        case _: ScalaFile =>
          if (leaf.getNextSibling != null && leaf.getNextSibling.getNextSibling.isInstanceOf[ScPackaging] &&
            leaf.getNextSibling.getNextSibling.getText.indexOf('{') == -1)
            return false
        case _ =>
      }
      parent match {
        case _: ScCaseClause =>
          if (parent.getNode.findChildByType(ScalaTokenTypes.tFUNTYPE) != null) return true
          else return false
        case _: ScMatchStmt => return true
        case _: ScalaFile | _: ScPackaging =>
          var node = leaf.getPrevSibling
          if (node.isInstanceOf[PsiWhiteSpace]) node = node.getPrevSibling
          node match {
            case x: PsiErrorElement => {
              val s = ErrMsg("wrong.top.statment.declaration")
              x.getErrorDescription match {
                case `s` => return true
                case _ => return false
              }
            }
            case _ => return true
          }
        case _ =>
      }
      if (parent.getParent != null) {
        parent.getParent.getParent match {
          case _: ScCaseClause =>
            if (parent.getParent.getParent.getNode.findChildByType(ScalaTokenTypes.tFUNTYPE) != null) return true
            else return false
          case _ =>
        }
      }
      parent.getParent match {
        case _: ScBlockExpr | _: ScTemplateBody =>
          parent match {
            case _: ScReferenceExpression =>
            case _ => return false
          }
          if (leaf.getPrevSibling == null || leaf.getPrevSibling.getPrevSibling == null ||
            leaf.getPrevSibling.getPrevSibling.getNode.getElementType != ScalaTokenTypes.kDEF)
            return true
        case _: ScCaseClause =>
          if (parent.getParent.getNode.findChildByType(ScalaTokenTypes.tFUNTYPE) != null) return true
          else return false
        case _ =>
      }
      if (leaf.getPrevSibling != null &&
              leaf.getPrevSibling.getPrevSibling != null &&
              ((leaf.getPrevSibling.getPrevSibling.getNode.getElementType == ScalaElementTypes.MATCH_STMT &&
                      leaf.getPrevSibling.getPrevSibling.getLastChild.isInstanceOf[PsiErrorElement]) ||
               (leaf.getPrevSibling.getPrevSibling.getNode.getElementType == ScalaElementTypes.TRY_STMT &&
                leaf.getPrevSibling.getPrevSibling.getLastChild.isInstanceOf[ScCatchBlock] &&
                       leaf.getPrevSibling.getPrevSibling.getLastChild.getLastChild.isInstanceOf[PsiErrorElement]))
              )
        return true
      if (parent.isInstanceOf[ScTemplateBody]) {
        if (leaf.getPrevSibling != null &&
            leaf.getPrevSibling.getPrevSibling != null &&
            leaf.getPrevSibling.getPrevSibling.getLastChild != null &&
            leaf.getPrevSibling.getPrevSibling.getLastChild.getNode.getElementType == ScalaElementTypes.MATCH_STMT &&
            leaf.getPrevSibling.getPrevSibling.getLastChild.getText.indexOf('{') != -1 &&
            leaf.getPrevSibling.getPrevSibling.getLastChild.getLastChild.isInstanceOf[PsiErrorElement])
          return true
      }
    }
    false
  }

  def isClassAcceptable(hintClass: java.lang.Class[_]): Boolean = true

  @NonNls
  override def toString = "'case' keyword filter"
}