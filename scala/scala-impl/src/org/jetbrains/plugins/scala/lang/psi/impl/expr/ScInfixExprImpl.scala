package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.base.{ScStringLiteralImpl, literals}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScProjectionType

import scala.annotation.tailrec

class ScInfixExprImpl(node: ASTNode) extends MethodInvocationImpl(node) with ScInfixExpr {

  import ScInfixExprImpl._
  import resolve.ScalaResolveResult
  import result._

  override def argumentExpressions: Seq[ScExpression] = argsElement match {
    case right if right == left => Seq(right)
    case tuple: ScTuple => tuple.exprs
    case ScParenthesisedExpr(expression) => Seq(expression)
    case _: ScUnitExpr => Seq.empty
    case expression => Seq(expression)
  }

  protected override def innerType: TypeResult = {
    val ScInfixExpr(ElementText(baseText), operation, ElementText(argumentText)) = this

    import ScalaPsiElementFactory.createExpressionWithContextFromText
    operation.bind().collect {
      //this is assignment statement: x += 1 equals to x = x + 1
      case ScalaResolveResult(element, _) if element.name + "=" == operation.refName =>
        createExpressionWithContextFromText(s"$baseText = $baseText ${element.name} $argumentText",
          getContext, this).`type`()
      case ScalaResolveResult(synth: ScSyntheticFunction, _) =>
        @tailrec
        def foldConstTypes(left: Option[ScType], right: Option[ScType]): TypeResult = (left, right) match {
          case (Some(ScLiteralType(valueLeft, _)), Some(ScLiteralType(valueRight, _))) =>
            Option(evaluateConstInfix(valueLeft.value, valueRight.value, synth.name))
              .fold(super.innerType) { value =>
                Right(ScLiteralType(value)(synth.getProject))
              }
          case (Some(ScProjectionType(_, element: Typeable)), _) =>
            foldConstTypes(element.`type`().toOption.filter(_.is[ScLiteralType]), right)
          case (_, Some(ScProjectionType(_, element: Typeable))) =>
            foldConstTypes(left, element.`type`().toOption.filter(_.is[ScLiteralType]))
          case _ => super.innerType
        }

        foldConstTypes(left.getNonValueType().toOption, right.getNonValueType().toOption)
    }.getOrElse(super.innerType)
  }

  override def toString: String = "InfixExpression"
}

object ScInfixExprImpl {

  import literals._

  private def evaluateConstInfix(left: Any, right: Any, name: String) =
    util.LiteralEvaluationUtil.evaluateConstInfix(left, right, name) match {
      case value: Int => ScIntegerLiteralImpl.Value(value)
      case value: Long => ScLongLiteralImpl.Value(value)
      case value: Float => ScFloatLiteralImpl.Value(value)
      case value: Double => ScDoubleLiteralImpl.Value(value)
      case value: Boolean => ScBooleanLiteralImpl.Value(value)
      case value: Char => ScCharLiteralImpl.Value(value)
      case value: Symbol => ScSymbolLiteralImpl.Value(value)
      case value: String => ScStringLiteralImpl.Value(value)
      case _ => null
    }

}
