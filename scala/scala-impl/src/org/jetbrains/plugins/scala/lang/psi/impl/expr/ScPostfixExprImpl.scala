package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import scala.collection.Seq

import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult

/**
  * @author Alexander Podkhalyuzin
  *         Date: 06.03.2008
  */
class ScPostfixExprImpl(node: ASTNode) extends MethodInvocationImpl(node) with ScPostfixExpr {

  def argumentExpressions: Seq[ScExpression] = Seq.empty

  def getInvokedExpr: ScExpression = operation

  def argsElement: PsiElement = operation

  override def toString: String = "PostfixExpression"

  override protected def innerType: TypeResult = getEffectiveInvokedExpr.getNonValueType()
}