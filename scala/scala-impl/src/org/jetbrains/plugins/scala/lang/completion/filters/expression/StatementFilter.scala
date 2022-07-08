package org.jetbrains.plugins.scala
package lang
package completion
package filters.expression

import com.intellij.psi._
import com.intellij.psi.filters.ElementFilter
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil._
import org.jetbrains.plugins.scala.lang.parser._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScStableReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody

class StatementFilter extends ElementFilter {
  override def isAcceptable(element: Object, context: PsiElement): Boolean = {
    if (context.is[PsiComment]) return false
    val leaf = getLeafByOffset(context.getTextRange.getStartOffset, context)
    if (leaf != null) {
      val parent = leaf.getParent
      if (parent.is[ScReferenceExpression] &&
              !parent.getParent.is[ScStableReferencePattern] &&
              (!parent.getParent.is[ScInfixExpr]) && (parent.getPrevSibling == null ||
              parent.getPrevSibling.getPrevSibling == null ||
        (parent.getPrevSibling.getPrevSibling.getNode.getElementType != ScalaElementType.MATCH_STMT ||
                      !parent.getPrevSibling.getPrevSibling.getLastChild.is[PsiErrorElement]))) {
        parent.getParent match {
          case _: ScBlockExpr | _: ScBlock | _: ScTemplateBody => return true
          case x: ScExpression => return checkReplace(x, "if (true) true")
          case _ =>
        }
        return true
      }
    }
    false
  }

  override def isClassAcceptable(hintClass: java.lang.Class[_]): Boolean = {
    true
  }

  @NonNls
  override def toString: String = {
    "statements keyword filter"
  }
}