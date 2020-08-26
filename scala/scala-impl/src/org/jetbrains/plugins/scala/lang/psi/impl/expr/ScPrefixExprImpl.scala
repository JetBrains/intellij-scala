package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.base.literals.NumericLiteralImplBase
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScProjectionType
import org.jetbrains.plugins.scala.lang.psi.types.result.{TypeResult, Typeable}
import org.jetbrains.plugins.scala.lang.psi.types.{ScLiteralType, ScType}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

import scala.annotation.tailrec

/**
  * @author Alexander Podkhalyuzin
  *         Date: 06.03.2008
  */
class ScPrefixExprImpl(node: ASTNode) extends MethodInvocationImpl(node) with ScPrefixExpr {

  override def argumentExpressions: Seq[ScExpression] = Seq.empty

  override def getInvokedExpr: ScExpression = operation

  override def toString: String = "PrefixExpression"

  override protected def innerType: TypeResult = {
    def default = getEffectiveInvokedExpr.getNonValueType()

    operation.bind().collect {
      case ScalaResolveResult(synth: ScSyntheticFunction, _) =>
        @tailrec
        def fold(expr: Option[ScType]): TypeResult = expr match {
          case Some(literal: ScLiteralType) =>
            foldUnOpTypes(literal, synth.name)(getProject)
              .fold(default)(Right.apply)
          case Some(ScProjectionType(_, element: Typeable)) =>
            fold(element.`type`().toOption.filter(_.isInstanceOf[ScLiteralType]))
          case _ => default
        }

        fold(operand.getNonValueType().toOption)
    }.getOrElse(default)
  }

  //TODO we have also support for Byte and Short, but that's not a big deal since literal types for them currently can't be parsed
  private def foldUnOpTypes(literal: ScLiteralType, name: String)
                           (implicit project: Project): Option[ScLiteralType] = literal.value match {
    case value: NumericLiteralImplBase.Value[_] =>
      name match {
        case "unary_+" => Some(literal)
        case "unary_-" => Some(ScLiteralType(value.negate))
        case _ => None
      }
    case _ => None
  }
}