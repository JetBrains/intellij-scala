package org.jetbrains.plugins.scala.lang.psi.uast.expressions

import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.uast.baseAdapters.{
  ScUAnnotated,
  ScUElement
}
import org.jetbrains.plugins.scala.lang.psi.uast.internals.LazyUElement
import org.jetbrains.uast.UExpressionAdapter

// TODO: remove or replace all `UastEmptyExpression` with it
/**
  * Temporary mock for all unsupported expressions.
  *
  * @param psiElement PSI element representing unsupported expression
  */
class ScUnknownExpression(@Nullable psiElement: PsiElement,
                          override protected val parent: LazyUElement)
    extends UExpressionAdapter
    with ScUElement
    with ScUAnnotated {

  override type PsiFacade = PsiElement

  override val scElement: PsiElement = psiElement

  override def asLogString(): String = "ScUnknownExpression(scElement)"
}
