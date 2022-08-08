package org.jetbrains.plugins.scala.lang.completion.filters.modifiers

import com.intellij.psi._
import com.intellij.psi.filters.ElementFilter
import org.jetbrains.annotations.{NonNls, Nullable}
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._

class ModifiersFilter extends ElementFilter {
  override def isAcceptable(element: Object, @Nullable context: PsiElement): Boolean = {
    if (context.is[PsiComment] || element.is[PsiIdentifier]) return false
    val leaf = ScalaCompletionUtil.getLeafOfContext(context)

    if (leaf != null) {
      val parent = leaf.getParent
      parent match {
        case _: ScClassParameter =>
          return true
        case _ =>
      }
      val tuple = ScalaCompletionUtil.getForAll(parent, leaf)
      if (tuple._1) return tuple._2

      return ScalaCompletionUtil.checkAfterSoftModifier(parent, leaf)
    }
    false
  }

  override def isClassAcceptable(hintClass: java.lang.Class[_]) = true

  @NonNls
  override def toString = "modifiers keyword filter"
}