package org.jetbrains.plugins.scala.lang.psi.api.base

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.{ImplicitArgumentsOwner, InvocationDetailsOwner}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeArgs
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter

trait ConstructorInvocationLike extends PsiElement with ImplicitArgumentsOwner with InvocationDetailsOwner {
  def typeArgList: Option[ScTypeArgs]

  def arguments: Seq[ScArgumentExprList]

  /** The arguments matched to the parameters of the constructor, one entry per argument list. */
  def matchedParametersByClauses: Seq[Seq[(ScExpression, Parameter)]]
}
