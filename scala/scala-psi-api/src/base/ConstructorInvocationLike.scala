package org.jetbrains.plugins.scala.lang.psi.api.base

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ImplicitArgumentsOwner
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeArgs
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScArgumentExprList

trait ConstructorInvocationLike extends PsiElement with ImplicitArgumentsOwner {
  def typeArgList: Option[ScTypeArgs]

  def arguments: Seq[ScArgumentExprList]
}
