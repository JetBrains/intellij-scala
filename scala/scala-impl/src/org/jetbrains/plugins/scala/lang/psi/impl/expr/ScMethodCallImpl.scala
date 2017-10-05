package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.expr._

import scala.collection.Seq

/**
 * @author Alexander Podkhalyuzin
 * Date: 06.03.2008
 */

class ScMethodCallImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScMethodCall {
  def getInvokedExpr: ScExpression = findChildByClassScala(classOf[ScExpression])

  def argumentExpressions: Seq[ScExpression] = if (args != null) args.exprs else Nil

  override def getEffectiveInvokedExpr: ScExpression = {
    findChildByClassScala(classOf[ScExpression]) match {
      case x: ScParenthesisedExpr => x.expr.getOrElse(x)
      case x => x
    }
  }

  override def argumentExpressionsIncludeUpdateCall: Seq[ScExpression] = {
    updateExpression() match {
      case Some(expr) => argumentExpressions ++ Seq(expr)
      case _ => argumentExpressions
    }
  }

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case visitor: ScalaElementVisitor => super.accept(visitor)
      case _ => super.accept(visitor)
    }
  }

  override def toString: String = "MethodCall"
}