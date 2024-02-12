package org.jetbrains.plugins.scala.lang.completion.filters.modifiers

import com.intellij.psi.filters.ElementFilter
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiComment, PsiElement, PsiIdentifier}
import org.jetbrains.annotations.{NonNls, Nullable}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil._

class SoftModifiersFilter extends ElementFilter {
  override def isAcceptable(element: Object, @Nullable context: PsiElement): Boolean = {
    if (context == null || !context.isInScala3File || context.is[PsiComment, PsiIdentifier]) return false
    val leaf = PsiTreeUtil.getDeepestFirst(context)

    val parent = leaf.getParent
    getForAll(parent, leaf) match {
      case (true, result) => result
      case (false, _) => checkAfterSoftModifier(parent, leaf)
    }
  }

  override def isClassAcceptable(hintClass: Class[_]): Boolean = true

  @NonNls
  override def toString: String = "soft modifiers keyword filter"
}
