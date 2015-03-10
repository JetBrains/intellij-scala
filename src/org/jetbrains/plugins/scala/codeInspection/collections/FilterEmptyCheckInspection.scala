package org.jetbrains.plugins.scala
package codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

/**
 * Nikolay.Tropin
 * 2014-05-07
 */
class FilterEmptyCheckInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] =
    Array(FilterIsEmptyCheck, FilterNonEmptyCheck, FilterNotIsEmptyCheck, FilterNotNonEmptyCheck)
}

object FilterIsEmptyCheck extends SimplificationType {
  override def hint = InspectionBundle.message("filter.empty.check.hint")

  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    expr match {
      case CheckIsEmpty(qual`.filter`(pred)) if !hasSideEffects(pred) =>
        val notExistsText = invocationText(negation = true, qual, "exists", pred)
        Some(replace(expr).withText(notExistsText).highlightFrom(qual))
      case _ => None
    }
  }
}

object FilterNonEmptyCheck extends SimplificationType {
  override def hint: String = InspectionBundle.message("filter.nonempty.check.hint")

  override def getSimplification(expr: ScExpression): Option[Simplification] = expr match {
    case CheckNonEmpty(qual`.filter`(pred)) if !hasSideEffects(pred) =>
      val existsText = invocationText(qual, "exists", pred)
      Some(replace(expr).withText(existsText).highlightFrom(qual))
    case _ => None
  }
}

object FilterNotIsEmptyCheck extends SimplificationType {
  override def hint: String = InspectionBundle.message("filterNot.empty.check.hint")

  override def getSimplification(expr: ScExpression): Option[Simplification] = expr match {
    case CheckIsEmpty(qual`.filterNot`(pred)) =>
      val forallText = invocationText(qual, "forall", pred)
      Some(replace(expr).withText(forallText).highlightFrom(qual))
    case _ => None
  }
}

object FilterNotNonEmptyCheck extends SimplificationType {
  override def hint: String = InspectionBundle.message("filterNot.nonempty.check.hint")

  override def getSimplification(expr: ScExpression): Option[Simplification] = expr match {
    case CheckNonEmpty(qual`.filterNot`(pred)) =>
      val notForallText = invocationText(negation = true, qual, "forall", pred)
      Some(replace(expr).withText(notForallText).highlightFrom(qual))
    case _ => None
  }
}