package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult

class ScPostfixExprImpl(node: ASTNode) extends MethodInvocationImpl(node) with ScPostfixExpr {

  override def argumentExpressions: Seq[ScExpression] = Seq.empty

  override def getInvokedExpr: ScExpression = operation

  override def argsElement: PsiElement = operation

  override def toString: String = "PostfixExpression"

  // the operation has no expected type, the expected type of this expression is taken into account
  // when its type is adapted to it, see ScExpression.getTypeWithoutImplicits
  override protected def innerType(expectedType: Option[ScType]): TypeResult =
    getEffectiveInvokedExpr.getNonValueType(expectedType = None)
}