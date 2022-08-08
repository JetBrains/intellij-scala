package org.jetbrains.plugins.scala.lang.completion.filters.expression

import com.intellij.psi._
import com.intellij.psi.filters.ElementFilter
import org.jetbrains.annotations.{NonNls, Nullable}
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil._
import org.jetbrains.plugins.scala.lang.parser._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScStableReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.expr._

class ExpressionFilter extends ElementFilter {
  override def isAcceptable(element: Object, @Nullable context: PsiElement): Boolean = {
    if (context == null || context.isInstanceOf[PsiComment]) return false
    val leaf = getLeafOfContext(context)
    if (leaf != null) {
      val parent = leaf.getParent
      if (parent.isInstanceOf[ScReferenceExpression] && !parent.getParent.isInstanceOf[ScPostfixExpr] &&
              !parent.getParent.isInstanceOf[ScStableReferencePattern] &&
              (parent.getPrevSibling == null ||
              parent.getPrevSibling.getPrevSibling == null ||
                (parent.getPrevSibling.getPrevSibling.getNode.getElementType != ScalaElementType.MATCH_STMT || !parent.getPrevSibling.getPrevSibling.getLastChild.isInstanceOf[PsiErrorElement]))) {
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
    "simple expressions keyword filter"
  }
}