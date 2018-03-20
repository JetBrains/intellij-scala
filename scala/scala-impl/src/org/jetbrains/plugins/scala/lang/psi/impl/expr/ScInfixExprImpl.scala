package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult
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
        s"$baseText = $baseText ${element.name} $argumentText"
    }.map {
      createExpressionWithContextFromText(_, getContext, this).`type`()
    }.getOrElse(super.innerType)
  }

  override def toString: String = "InfixExpression"
}
