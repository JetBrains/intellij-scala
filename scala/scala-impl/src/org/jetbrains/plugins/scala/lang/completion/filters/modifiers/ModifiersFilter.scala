package org.jetbrains.plugins.scala.lang.completion.filters.modifiers

import com.intellij.psi._
import com.intellij.psi.filters.ElementFilter
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.{NonNls, Nullable}
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._

class ModifiersFilter extends ElementFilter {
  override def isAcceptable(element: Object, @Nullable context: PsiElement): Boolean = {
    if (context == null || context.is[PsiComment] || element.is[PsiIdentifier]) return false
    val leaf = PsiTreeUtil.getDeepestFirst(context)

    val parent = leaf.getParent
    parent match {
      case _: ScClassParameter =>
        true
      case _ =>
        ScalaCompletionUtil.getForAll(parent, leaf) match {
          case (true, result) => result
          case (false, _) => ScalaCompletionUtil.checkAfterSoftModifier(parent, leaf)
        }
    }
  }

  override def isClassAcceptable(hintClass: java.lang.Class[_]) = true

  @NonNls
  override def toString = "modifiers keyword filter"
}