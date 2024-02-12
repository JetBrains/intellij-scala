package org.jetbrains.plugins.scala.lang.completion.filters.modifiers

import com.intellij.psi.filters.ElementFilter
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiComment, PsiElement}
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.scala.extensions.{&, ObjectExt, Parent, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScImportExpr, ScImportSelector}

final class GivenImportSelectorFilter extends ElementFilter {
  override def isAcceptable(element: Any, context: PsiElement): Boolean = {
    if (context == null || !context.isInScala3File || context.is[PsiComment]) return false
    val leaf = PsiTreeUtil.getDeepestFirst(context)

    leaf.getParent match {
      case (ref: ScStableCodeReference) & Parent(_: ScImportExpr) =>
        ref.qualifier.isDefined
      case _: ScImportSelector | (_: ScStableCodeReference) & Parent(_: ScImportSelector) =>
        true
      case _ => false
    }
  }

  override def isClassAcceptable(hintClass: Class[_]): Boolean = true

  @NonNls
  override def toString: String = "'given' keyword in import selector filter"
}
