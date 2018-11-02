package org.jetbrains.plugins.scala
package lang
package completion
package filters.definitions

import com.intellij.psi._
import com.intellij.psi.filters.ElementFilter
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil._
import org.jetbrains.plugins.scala.lang.lexer._
import org.jetbrains.plugins.scala.lang.parser._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._

import scala.annotation.tailrec

/**
  * @author Alexander Podkhalyuzin
  *         Date: 22.05.2008
  */

class DefinitionsFilter extends ElementFilter {
  def isAcceptable(element: Object, context: PsiElement): Boolean = {
    if (context.isInstanceOf[PsiComment]) return false
    val leaf = getLeafByOffset(context.getTextRange.getStartOffset, context)
    if (leaf != null) {
      val parent = leaf.getParent
      parent match {
        case _: ScClassParameter =>
          return true
        case _: ScReferenceExpression =>
        case _ => return false
      }
      @tailrec
      def findParent(p: PsiElement): PsiElement = {
        if (p == null) return null
        p.getParent match {
          case parent@(_: ScBlock | _: ScCaseClause | _: ScTemplateBody | _: ScClassParameter | _: ScalaFile) =>
            parent match {
              case clause: ScCaseClause =>
                clause.funType match {
                  case Some(elem) => if (leaf.getTextRange.getStartOffset <= elem.getTextRange.getStartOffset) return null
                  case _ => return null
                }
              case _ =>
            }
            if (!parent.isInstanceOf[ScalaFile] || parent.asInstanceOf[ScalaFile].isScriptFile)
              if ((leaf.getPrevSibling == null || leaf.getPrevSibling.getPrevSibling == null ||
                leaf.getPrevSibling.getPrevSibling.getNode.getElementType != ScalaTokenTypes.kDEF) &&
                (parent.getPrevSibling == null || parent.getPrevSibling.getPrevSibling == null ||
                  (parent.getPrevSibling.getPrevSibling.getNode.getElementType != ScalaElementType.MATCH_STMT ||
                    !parent.getPrevSibling.getPrevSibling.getLastChild.isInstanceOf[PsiErrorElement])))
                return p
            null
          case _ => findParent(p.getParent)
        }
      }
      val otherParent = findParent(parent)
      if (otherParent != null && otherParent.getTextRange.getStartOffset == parent.getTextRange.getStartOffset)
        return true
    }
    false
  }

  def isClassAcceptable(hintClass: java.lang.Class[_]): Boolean = {
    true
  }

  @NonNls
  override def toString: String = {
    "val, var keyword filter"
  }
}