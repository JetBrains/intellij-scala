package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil.ImplicitArgumentsClause
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types.result._

class ScParenthesisedExprImpl(node: ASTNode) extends ScExpressionImplBase(node) with ScParenthesisedExpr {

  protected override def innerType: TypeResult = {
    innerElement match {
      case Some(x: ScExpression) =>
        val res = x.getNonValueType()
        res
      case _ => Failure(ScalaBundle.message("no.expression.in.parentheses"))
    }
  }

  // implicit arguments are owned by inner element
  override def findImplicitArguments: Seq[ImplicitArgumentsClause] = Seq.empty

  override def toString: String = "ExpressionInParenthesis"
}