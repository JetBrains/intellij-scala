package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import org.jetbrains.plugins.scala.lang.psi.api._


import com.intellij.psi.{PsiElement, PsiReference}
import org.jetbrains.plugins.scala.lang.psi.api.base.ConstructorInvocationLike
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

/**
  * @author Alexander Podkhalyuzin
  *         Date: 22.02.2008
  */
trait ScSelfInvocationBase extends ScalaPsiElementBase with PsiReference with ConstructorInvocationLike { this: ScSelfInvocation =>
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