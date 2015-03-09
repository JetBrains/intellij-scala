package org.jetbrains.plugins.scala
package codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.extensions.childOf
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScInfixExpr}

/**
 * Nikolay.Tropin
 * 2014-05-07
 */
class FilterSizeCheckInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] =
    Array(FilterSizeCheck)
}

object FilterSizeCheck extends SimplificationType {
  override def hint = InspectionBundle.message("filter.size.check.hint")

  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    expr match {
      case checkNonEmpty(qual`.filter`(pred)) if !hasSideEffects(pred) =>
        Some(replace(expr).withText(existsText(qual, pred)).highlightFrom(qual))
      case checkIsEmpty(qual`.filter`(pred)) if !hasSideEffects(pred) =>
        Some(replace(expr).withText(notExistsText(qual, pred)).highlightFrom(qual))
      case _ => None
    }
  }

  private def existsText(qual: ScExpression, pred: ScExpression): String = invocationText(qual, "exists", pred)

  private def notExistsText(qual: ScExpression, pred: ScExpression): String = {
    qual match {
      case _ childOf ScInfixExpr(`qual`, _, _) => s"!(${existsText(qual, pred)})"
      case _ => s"!${existsText(qual, pred)}"
    }
  }

  object checkIsEmpty {
    def unapply(expr: ScExpression): Option[ScExpression] = {
      expr match {
        case (qual`.sizeOrLength`()) `==` literal("0") => Some(qual)
        case qual`.isEmpty`() => Some(qual)
        case `!`(qual`.nonEmpty`()) => Some(qual)
        case _ => None
      }
    }
  }

  object checkNonEmpty {
    def unapply(expr: ScExpression): Option[ScExpression] = {
      expr match {
        case (qual`.sizeOrLength`()) `!=` literal("0") => Some(qual)
        case (qual`.sizeOrLength`()) `>` literal("0") => Some(qual)
        case (qual`.sizeOrLength`()) `>=` literal("1") => Some(qual)
        case `!`(qual`.isEmpty`()) => Some(qual)
        case _ => None
      }
    }
  }

}