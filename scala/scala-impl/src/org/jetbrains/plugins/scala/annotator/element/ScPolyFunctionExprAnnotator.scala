package org.jetbrains.plugins.scala.annotator.element

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.lang.psi.api.base.ScParenthesizedElement.InnermostElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScParenthesisedTypeElement.InnermostTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScDependentFunctionTypeElement, ScFunctionalTypeElement, ScPolyFunctionTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScPolyFunctionExpr, ScUnderScoreSectionUtil}

object ScPolyFunctionExprAnnotator extends ElementAnnotator[ScPolyFunctionExpr] {
  override def annotate(
    element:   ScPolyFunctionExpr,
    typeAware: Boolean
  )(implicit
    holder: ScalaAnnotationHolder
  ): Unit = element.result match {
    case None | Some(FunctionLiteral()) => ()
    case Some(nonFn) =>
      holder.createErrorAnnotation(
        nonFn,
        ScalaBundle.message("poly.function.without.parameters", "literals")
      )
  }

  private object FunctionLiteral {
    def unapply(body: ScExpression): Boolean =
      ScPolyFunctionExpr.functionLiteral(body).isDefined || (body match {
        // a function in placeholder syntax, e.g. `[A] => (_: List[A]).size`
        case InnermostElement(inner: ScExpression) => ScUnderScoreSectionUtil.isUnderscoreFunction(inner)
        case _                                     => false
      })
  }
}

object ScPolyFunctionTypeElementAnnotator extends ElementAnnotator[ScPolyFunctionTypeElement] {
  override def annotate(
    element:   ScPolyFunctionTypeElement,
    typeAware: Boolean
  )(implicit
    holder: ScalaAnnotationHolder
  ): Unit = element.resultTypeElement match {
    case None | Some(InnermostTypeElement(_: ScFunctionalTypeElement | _: ScDependentFunctionTypeElement)) => ()
    case Some(nonFn) =>
      holder.createErrorAnnotation(
        nonFn,
        ScalaBundle.message("poly.function.without.parameters", "types")
      )
  }
}
