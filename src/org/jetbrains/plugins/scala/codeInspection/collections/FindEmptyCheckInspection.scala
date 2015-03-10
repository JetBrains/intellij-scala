package org.jetbrains.plugins.scala
package codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

/**
 * Nikolay.Tropin
 * 2014-05-05
 */
class FindEmptyCheckInspection extends OperationOnCollectionInspection{
  override def possibleSimplificationTypes: Array[SimplificationType] =
    Array(FindIsDefined, FindIsEmpty)
}

object FindIsDefined extends SimplificationType() {
  def hint = InspectionBundle.message("find.isDefined.hint")

  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    expr match {
      case CheckIsDefined(qual`.find`(cond)) =>
        val existsText = invocationText(qual, "exists", cond)
        Some(replace(expr).withText(existsText).highlightFrom(qual))
      case _ => None
    }
  }
}

object FindIsEmpty extends SimplificationType() {
  def hint = InspectionBundle.message("find.isEmpty.hint")

  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    expr match {
      case CheckIsEmpty(qual`.find`(cond)) =>
        val notExistsText = invocationText(negation = true, qual, "exists", cond)
        Some(replace(expr).withText(notExistsText).highlightFrom(qual))
      case _ => None
    }
  }
}