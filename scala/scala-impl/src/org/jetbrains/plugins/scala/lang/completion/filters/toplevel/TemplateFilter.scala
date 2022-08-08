package org.jetbrains.plugins.scala.lang.completion.filters.toplevel

import com.intellij.psi.filters.ElementFilter
import com.intellij.psi._
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
    
    if (leaf != null) {
      val parent = leaf.getParent
      val tuple = ScalaCompletionUtil.getForAll(parent, leaf)
      if (tuple._1) return tuple._2
      parent match {
        case _: ScReferenceExpression =>
          parent.getParent match {
            case y: ScStableReferencePattern =>
              y.getParent match {
                case x: ScCaseClause =>
                  x.getParent.getParent match {
                    case _: ScMatch if x.getParent.getFirstChild == x => return false
                    case _ => return true
                  }
                case _ =>
              }
            case _ =>
              return checkAfterSoftModifier(parent, leaf)
          }
        case _ =>
      }
    }
    false
  }

  override def isClassAcceptable(hintClass: java.lang.Class[_]): Boolean = true

  @NonNls
  override def toString = "template definitions keyword filter"
}