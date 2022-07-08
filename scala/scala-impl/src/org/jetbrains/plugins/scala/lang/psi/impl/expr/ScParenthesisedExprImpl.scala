package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

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
  override def findImplicitArguments: Option[Seq[ScalaResolveResult]] = None

  override def toString: String = "ExpressionInParenthesis"
}