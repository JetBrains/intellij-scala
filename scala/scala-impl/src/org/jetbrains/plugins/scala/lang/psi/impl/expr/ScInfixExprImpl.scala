package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.types.{ScLiteralType, ScType}
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScProjectionType
import org.jetbrains.plugins.scala.lang.psi.types.result.{TypeResult, Typeable}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

/**
  * @author Alexander Podkhalyuzin
  *         Date: 06.03.2008
  */
class ScInfixExprImpl(node: ASTNode) extends MethodInvocationImpl(node) with ScInfixExpr {

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
        def foldConstTypes(left: Option[ScType], right: Option[ScType]): TypeResult = (left, right) match {
          case (Some(l: ScLiteralType), Some(r: ScLiteralType)) =>
            ScLiteralType.foldBinOpTypes(l, r, synth).map(Right(_)).getOrElse(super.innerType)
          case (Some(ScProjectionType(_, element: Typeable)), _) =>
            foldConstTypes(element.`type`().toOption.filter(_.isInstanceOf[ScLiteralType]), right)
          case (_, Some(ScProjectionType(_, element: Typeable))) =>
            foldConstTypes(left, element.`type`().toOption.filter(_.isInstanceOf[ScLiteralType]))
          case _ => super.innerType
        }
        foldConstTypes(lOp.`type`().toOption, rOp.`type`().toOption)
    }.getOrElse(super.innerType)
  }

  override def toString: String = "InfixExpression"
}
