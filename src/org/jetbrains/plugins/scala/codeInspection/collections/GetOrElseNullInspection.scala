package org.jetbrains.plugins.scala
package codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

/**
 * Nikolay.Tropin
 * 2014-05-06
 */
class GetOrElseNullInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] =
    Array(GetOrElseNull)
}

object GetOrElseNull extends SimplificationType {
  override def getSimplification(expr: ScExpression) = {
    expr match {
      case qual`.getOrElse`(literal("null")) =>
        Some(replace(expr).withText(invocationText(qual, "orNull")))
      case _ => None
    }
  }

  override def hint = InspectionBundle.message("getOrElse.null.hint")
}
