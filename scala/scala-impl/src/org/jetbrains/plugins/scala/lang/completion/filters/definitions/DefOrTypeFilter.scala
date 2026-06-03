package org.jetbrains.plugins.scala.lang.completion.filters.definitions

import com.intellij.psi._
import com.intellij.psi.filters.ElementFilter
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.{NonNls, Nullable}
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil._
import org.jetbrains.plugins.scala.lang.psi.ScDeclarationSequenceHolder
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._

class DefOrTypeFilter extends ElementFilter {
  override def isAcceptable(element: Object, @Nullable context: PsiElement): Boolean = {
    if (context == null || context.is[PsiComment]) return false
    val leaf = PsiTreeUtil.getDeepestFirst(context)
    val parent = leaf.getParent
    parent match {
      case _: ScReferenceExpression =>
      case _ => return false
    }
    parent.getParent match {
      case parent@(_: ScDeclarationSequenceHolder |
                   _: ScCaseClause |
                   _: ScTemplateBody |
                   _: ScClassParameter) =>
        awful(parent, leaf)
      case _ =>
        checkAfterSoftModifier(parent, leaf)
    }
  }

  override def isClassAcceptable(hintClass: java.lang.Class[_]): Boolean = {
    true
  }

  @NonNls
  override def toString: String = {
    "'def', 'type' keyword filter"
  }
}