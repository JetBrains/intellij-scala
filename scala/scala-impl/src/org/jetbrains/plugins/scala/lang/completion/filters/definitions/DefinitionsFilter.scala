package org.jetbrains.plugins.scala
package lang
package completion
package filters
package definitions

import com.intellij.psi._
import com.intellij.psi.filters.ElementFilter
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil._
import org.jetbrains.plugins.scala.lang.psi.ScDeclarationSequenceHolder
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._

import scala.annotation.tailrec

class DefinitionsFilter extends ElementFilter {
  override def isAcceptable(element: Object, context: PsiElement): Boolean = {
    if (context.is[PsiComment]) return false
    val leaf = getLeafByOffset(context.getTextRange.getStartOffset, context)
    if (leaf != null) {
      val parent = leaf.getParent
      parent match {
        case _: ScClassParameter =>
          return true
        case _: ScReferenceExpression if checkAfterSoftModifier(parent, leaf) => return true
        case _: ScReferenceExpression =>
        case _ => return false
      }
      @tailrec
      def findParent(p: PsiElement): PsiElement = {
        if (p == null) return null
        p.getParent match {
          case parent@(_: ScDeclarationSequenceHolder |
                       _: ScCaseClause |
                       _: ScTemplateBody |
                       _: ScClassParameter) =>
            parent match {
              case clause: ScCaseClause =>
                clause.funType match {
                  case Some(elem) => if (leaf.getTextRange.getStartOffset <= elem.getTextRange.getStartOffset) return null
                  case _ => return null
                }
              case _ =>
            }
            if (awful(parent, leaf))
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

  override def isClassAcceptable(hintClass: java.lang.Class[_]): Boolean = {
    true
  }

  @NonNls
  override def toString: String = {
    "val, var keyword filter"
  }
}