package org.jetbrains.plugins.scala.lang.psi.api

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeArgs
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScArgumentExprList

import scala.collection.Seq

trait ConstructorInvocationLike extends PsiElement {
  def typeArgList: Option[ScTypeArgs]

  def arguments: Seq[ScArgumentExprList]
}
