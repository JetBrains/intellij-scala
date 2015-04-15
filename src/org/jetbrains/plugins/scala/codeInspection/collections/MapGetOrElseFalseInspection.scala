package org.jetbrains.plugins.scala
package codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

/**
 * Nikolay.Tropin
 * 2014-05-05
 */
class MapGetOrElseFalseInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] =
    Array(MapGetOrElseFalse)
}

object MapGetOrElseFalse extends SimplificationType() {
  def hint = InspectionBundle.message("map.getOrElse.false.hint")

  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    expr match {
      case qual`.map`(f @ returnsBoolean())`.getOrElse`(literal("false")) =>
        Some(replace(expr).withText(invocationText(qual, "exists", f)).highlightFrom(qual))
      case _ => None
    }
  }
}
