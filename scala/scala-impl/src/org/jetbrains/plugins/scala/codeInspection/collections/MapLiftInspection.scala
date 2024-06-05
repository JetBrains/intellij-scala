package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

import scala.collection.immutable.ArraySeq

class MapLiftInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: ArraySeq[SimplificationType] = ArraySeq(MapLift)
}

object MapLift extends SimplificationType {
  override def hint: String = ScalaInspectionBundle.message("replace.with.get")

  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    expr match {
      case `.lift`(qual) `.apply` n if isMap(qual) =>
        Some(replace(expr).withText(invocationText(qual, "get", n)).highlightFrom(qual))
      case _ => None
    }
  }

}
