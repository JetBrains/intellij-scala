package org.jetbrains.plugins.scala.lang.completion.filters

import com.intellij.psi.PsiElement
import com.intellij.psi.filters.ElementFilter
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.project.ProjectPsiElementExt

object IsInScala3ModuleFilter extends ElementFilter {
  override def toString: String = "IsInScala3ModuleFilter"

  override def isClassAcceptable(hintClass: Class[_]): Boolean = true

  override def isAcceptable(element: Object, @Nullable context: PsiElement): Boolean =
    context != null && context.isInScala3Module
}
