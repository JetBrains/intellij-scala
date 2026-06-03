package org.jetbrains.plugins.scala.lang.completion.filters.toplevel

import com.intellij.psi._
import com.intellij.psi.filters.ElementFilter
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.{NonNls, Nullable}
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScStableReferencePattern}
import org.jetbrains.plugins.scala.lang.psi.api.expr._

class TemplateFilter extends ElementFilter {
  override def isAcceptable(element: Object, @Nullable context: PsiElement): Boolean = {
    if (context == null || context.is[PsiComment]) return false
    val leaf = PsiTreeUtil.getDeepestFirst(context)

    val parent = leaf.getParent
    val (stopHere, result) = ScalaCompletionUtil.getForAll(parent, leaf)
    if (stopHere) return result
    parent match {
      case _: ScReferenceExpression =>
        parent.getParent match {
          case y: ScStableReferencePattern =>
            y.getParent match {
              case x: ScCaseClause =>
                x.getParent.getParent match {
                  case _: ScMatch if x.getParent.getFirstChild == x => false
                  case _ => true
                }
              case _ => false
            }
          case _ =>
            checkAfterSoftModifier(parent, leaf)
        }
      case _ => false
    }
  }

  override def isClassAcceptable(hintClass: java.lang.Class[_]): Boolean = true

  @NonNls
  override def toString = "template definitions keyword filter"
}