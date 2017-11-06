package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

/**
  * @author t-kameyama
  */
class MapLiftInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] = Array(MapList)
}

object MapList extends SimplificationType {
  override def hint: String = InspectionBundle.message("replace.with.get")

  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    expr match {
      case qual `.lift` (n) if isMap(qual) =>
        Some(replace(expr).withText(invocationText(qual, "get", n)).highlightFrom(qual))
      case _ => None
    }
  }

}
