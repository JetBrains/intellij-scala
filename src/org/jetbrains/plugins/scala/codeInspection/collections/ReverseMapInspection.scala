package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

/**
 * @author Nikolay.Tropin
 */

object ReverseMap extends SimplificationType() {
  override def hint: String = InspectionBundle.message("replace.reverse.map")

  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    expr match {
      case qual`.reverse`()`.map`() =>
        Some(replace(expr).withText(invocationText(qual, "reverseMap")).highlightFrom(qual))
      case _ => None
    }
  }
}

class ReverseMapInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] = Array(ReverseMap)
}