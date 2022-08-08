package org.jetbrains.plugins.scala.lang.completion.filters.toplevel

import com.intellij.psi.filters.ElementFilter
import com.intellij.psi.{PsiComment, PsiElement}
import org.jetbrains
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.{NonNls, Nullable}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil

class ExportFilter extends ElementFilter {
  override def isAcceptable(element: Object, @Nullable context: PsiElement): Boolean = {
    if (context == null || !context.isInScala3File || context.is[PsiComment]) return false
    val leaf = PsiTreeUtil.getDeepestFirst(context)

    if (leaf != null) {
      val parent = leaf.getParent
      val (stopHere, res) = ScalaCompletionUtil.getForAll(parent, leaf)
      if (stopHere) return res
    }

    false
  }

  override def isClassAcceptable(hintClass: Class[_]): Boolean = true

  @NonNls
  override def toString: String = "export keyword filter"
}
