package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

/**
 * @author Nikolay.Tropin
 */
class MapFlattenInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] = Array(MapFlatten)
}

object MapFlatten extends SimplificationType {
  override def hint: String = InspectionBundle.message("replace.map.flatten.with.flatMap")

  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    expr match {
      case qual`.map`(f)`.flatten`() if implicitParameterExistsFor(expr) =>
        Some(replace(expr).withText(invocationText(qual, "flatMap", f)).highlightFrom(qual))
      case _ => None
    }
  }
}
