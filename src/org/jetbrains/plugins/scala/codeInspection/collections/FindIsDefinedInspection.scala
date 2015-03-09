package org.jetbrains.plugins.scala
package codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

/**
 * Nikolay.Tropin
 * 2014-05-05
 */
class FindIsDefinedInspection extends OperationOnCollectionInspection{
  override def possibleSimplificationTypes: Array[SimplificationType] =
    Array(FindIsDefined)
}

object FindIsDefined extends SimplificationType() {
  def hint = InspectionBundle.message("find.isDefined.hint")

  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    expr match {
      case qual`.find`(cond)`.isDefined`() =>
        Some(replace(expr).withText(invocationText(qual, "exists", cond)).highlightFrom(qual))
      case _ => None
    }
  }
}