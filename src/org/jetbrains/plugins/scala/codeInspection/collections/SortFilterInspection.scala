package org.jetbrains.plugins.scala
package codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr._

/**
 * Nikolay.Tropin
 * 2014-05-05
 */
class SortFilterInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] =
    Array(SortFilter)
}

object SortFilter extends SimplificationType {

  def hint = InspectionBundle.message("sort.filter.hint")

  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    expr match {
      case qual`.sort`()`.filter`(pred) if !hasSideEffects(pred) => swapLastTwoMethods(expr)
      case qual`.sort`(_)`.filter`(pred) if !hasSideEffects(pred) => swapLastTwoMethods(expr)
      case _ => None
    }
  }

  def swapLastTwoMethods(expr: ScExpression): Option[Simplification] = {
    def refWithArgumentsText(method: MethodRepr): Option[String] = (method.itself, method.optionalBase) match {
      case (_: ScMethodCall | _: ScReferenceExpression, Some(baseExpr)) =>
        val startIndex = baseExpr.getTextRange.getEndOffset - method.itself.getTextRange.getStartOffset
        val text = method.itself.getText
        if (startIndex > 0 && startIndex < text.length) Option(text.substring(startIndex))
        else None
      case (ScInfixExpr(left, op, right), _) =>
        def argListFromInfix(arg: ScExpression) = arg match {
          case x @ (_: ScBlock | _: ScParenthesisedExpr | _: ScTuple) => x.getText
          case _ => s"(${arg.getText})"
        }
        Some(s".${op.refName}${argListFromInfix(right)}")
      case _ => None
    }

    expr match {
      case MethodSeq(last, second, _*) =>
        for {
          lastText <- refWithArgumentsText(last)
          secondText <- refWithArgumentsText(second)
          baseExpr <- second.optionalBase
        } {

          val newText = s"${baseExpr.getText}$lastText$secondText"
          val qual = second.optionalBase.getOrElse(second.itself)
          val simplification = replace(expr).withText(newText).highlightFrom(qual)
          return Some(simplification)
        }
        None
      case _ => None
    }
  }
}