package org.jetbrains.plugins.scala
package codeInspection
package collections

import org.jetbrains.plugins.scala.lang.psi.api.expr._

import scala.collection.immutable.ArraySeq

class SortFilterInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: ArraySeq[SimplificationType] =
    ArraySeq(SortFilter)
}

object SortFilter extends SimplificationType {

  override def hint: String = ScalaInspectionBundle.message("sort.filter.hint")

  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    expr match {
      // TODO infix notation
      case `.sort`(_)`.filter`(pred) if !hasSideEffects(pred) => swapLastTwoMethods(expr)
      case _ `.sort`(_)`.filter`(pred) if !hasSideEffects(pred) => swapLastTwoMethods(expr)
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
      case (ScInfixExpr(_, op, right), _) =>
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
