package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScProjectionType
import org.jetbrains.plugins.scala.lang.psi.types.{ScLiteralType, ScType}

import scala.collection.Seq
import org.jetbrains.plugins.scala.lang.psi.types.result.{TypeResult, Typeable}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

/**
  * @author Alexander Podkhalyuzin
  *         Date: 06.03.2008
  */
class ScPrefixExprImpl(node: ASTNode) extends MethodInvocationImpl(node) with ScPrefixExpr {

  def argumentExpressions: Seq[ScExpression] = Seq.empty

  def getInvokedExpr: ScExpression = operation

  override def toString: String = "PrefixExpression"

  override protected def innerType: TypeResult = {
    def default = getEffectiveInvokedExpr.getNonValueType()
    operation.bind().collect{
      case ScalaResolveResult(synth: ScSyntheticFunction, _) =>
        def fold(expr: Option[ScType]): TypeResult = expr match {
          case Some(lit: ScLiteralType) =>
            ScLiteralType.foldUnOpTypes(lit, synth).map(Right(_)).getOrElse(default)
          case Some(ScProjectionType(_, element: Typeable, _)) =>
            fold(element.`type`().toOption.filter(_.isInstanceOf[ScLiteralType]))
          case _ => default
        }
        fold(operand.`type`().toOption)
    }.getOrElse(default)
  }
}