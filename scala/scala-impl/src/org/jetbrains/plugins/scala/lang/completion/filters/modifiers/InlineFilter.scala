package org.jetbrains.plugins.scala.lang.completion.filters.modifiers

import com.intellij.psi.filters.ElementFilter
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiComment, PsiElement, PsiIdentifier}
import org.jetbrains.annotations.{NonNls, Nullable}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.completion.ScalaKeyword
import org.jetbrains.plugins.scala.lang.completion.filters.modifiers.InlineFilter._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter

class InlineFilter extends ElementFilter {
  override def isAcceptable(element: Object, @Nullable context: PsiElement): Boolean = {
    if (context == null || !context.isInScala3File || context.is[PsiComment, PsiIdentifier]) return false
    val leaf = PsiTreeUtil.getDeepestFirst(context)
    val parent = leaf.getParent

    parent match {
      case param: ScParameter if isAfterLeftParen(param) && isInlineFunction(param.owner) => true
      case ref: ScReferenceExpression if isInlineFunction(ref.getParent) => true
      case _ => false
    }
  }

  override def isClassAcceptable(hintClass: Class[_]): Boolean = true

  @NonNls
  override def toString: String = "inline keyword filter"
}

object InlineFilter {
  private def isInlineFunction(elem: PsiElement): Boolean = elem match {
    case fn: ScFunction => fn.hasModifierPropertyScala(ScalaKeyword.INLINE)
    case _ => false
  }
}
