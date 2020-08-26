package org.jetbrains.plugins.scala
package lang
package psi
package api
package base

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeArgs
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScArgumentExprList

trait ConstructorInvocationLike extends PsiElement with ImplicitArgumentsOwner {
  def typeArgList: Option[ScTypeArgs]

  def arguments: collection.Seq[ScArgumentExprList]
}
