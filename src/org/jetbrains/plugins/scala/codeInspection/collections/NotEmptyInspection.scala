package org.jetbrains.plugins.scala
package codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

/**
 * Nikolay.Tropin
 * 2014-05-07
 */
class NotIsEmptyInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] =
    Array(NotIsEmpty)
}

object NotIsEmpty extends SimplificationType() {
  def hint = InspectionBundle.message("not.isEmpty.hint")

  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    expr match {
      case !(qual`.isEmptyOnOption`()) =>
        Some(replace(expr).withText(invocationText(qual, "isDefined", Seq.empty)).highlightFrom(qual))
      case !(qual`.isEmpty`()) =>
        Some(replace(expr).withText(invocationText(qual, "nonEmpty", Seq.empty)).highlightFrom(qual))
      case _ => None
    }
  }
}