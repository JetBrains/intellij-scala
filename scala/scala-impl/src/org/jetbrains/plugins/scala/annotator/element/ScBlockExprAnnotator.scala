package org.jetbrains.plugins.scala.annotator.element

import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockExpr, ScExpression, ScFunctionExpr}

//ScFunctionExprAnnotator and ScExpressionAnnotator may want to highlight closing brace of the enclosing block,
//but in the new API it's not possible to highlight external elements
object ScBlockExprAnnotator extends ElementAnnotator[ScBlockExpr] {
  override def annotate(element: ScBlockExpr, typeAware: Boolean)(implicit holder: ScalaAnnotationHolder): Unit =
    element match {
      case annotatedByBlockExpr(funExpr: ScFunctionExpr) => ScFunctionExprAnnotator.annotateImpl(funExpr, typeAware, fromBlock = true)
      case annotatedByBlockExpr(expr: ScExpression)      => ScExpressionAnnotator.annotateImpl(expr, typeAware, fromBlock = true)
      case _ =>
    }
}

private object annotatedByBlockExpr {
  def unapply(block: ScBlockExpr): Option[ScExpression] = block.exprs match {
    case Seq(funExpr: ScFunctionExpr) => Some(funExpr)
    case Seq(expr: ScExpression)      => Some(expr)
    case _                            => None
  }

  def apply(expr: ScExpression): Boolean = expr.getParent match {
    case annotatedByBlockExpr(_) => true
    case _ => false
  }
}
