package org.jetbrains.plugins.scala.lang.psi.api.expr

import com.intellij.psi.{PsiElement, PsiReference}
import org.jetbrains.plugins.scala.lang.psi.api.base.ConstructorInvocationLike
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaElementVisitor, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult
import org.jetbrains.plugins.scala.lang.resolve.{ResolveMode, ScalaResolveResult, ScalaResolveSupport}

trait ScSelfInvocation extends ScalaPsiElement with PsiReference with ConstructorInvocationLike {
  def args: Option[ScArgumentExprList]

  override def arguments: Seq[ScArgumentExprList]

  def bind: Option[PsiElement]

  def multiResolve: Seq[ScalaResolveResult]

  /**
   * @return the types of the constructors this invocation may resolve to (with the arguments checked according to `mode`),
   *         applied to the first `i` argument clauses
   */
  def multiType(i: Int, mode: ResolveMode = ResolveMode.Full): Array[TypeResult]

  def thisElement: PsiElement = getFirstChild

  override def getReference: PsiReference = ScalaResolveSupport.getInstance.getReference(this)

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitSelfInvocation(this)
  }
}
