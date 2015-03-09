package org.jetbrains.plugins.scala
package codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

/**
 * Nikolay.Tropin
 * 2014-05-05
 */
class FilterHeadOptionInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] =
    Array(FilterHeadOption)
}

object FilterHeadOption extends SimplificationType {

  def hint = InspectionBundle.message("filter.headOption.hint")

  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    expr match {
      case qual`.filter`(cond)`.headOption`() if !hasSideEffects(cond) =>
        Some(replace(expr).withText(invocationText(qual, "find", cond)).highlightFrom(qual))
      case _ => None
    }
  }
}
