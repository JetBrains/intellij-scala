package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import com.intellij.psi.{PsiElement, PsiReference}
import org.jetbrains.plugins.scala.lang.psi.api.base.ConstructorInvocationLike
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

trait ScSelfInvocation extends ScalaPsiElement with PsiReference with ConstructorInvocationLike {
  def args: Option[ScArgumentExprList]

  override def arguments: Seq[ScArgumentExprList]

  def bind: Option[PsiElement]

  def multiResolve: Seq[ScalaResolveResult]

  def shapeType(i: Int): TypeResult

  def shapeMultiType(i: Int): Array[TypeResult]

  def multiType(i: Int): Array[TypeResult]

  def thisElement: PsiElement = getFirstChild

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitSelfInvocation(this)
  }
}