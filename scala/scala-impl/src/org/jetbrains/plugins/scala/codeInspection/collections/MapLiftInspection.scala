package org.jetbrains.plugins.scala
package codeInspection
package collections

import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

import scala.collection.immutable.ArraySeq

class MapLiftInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: ArraySeq[SimplificationType] = ArraySeq(MapList)
}

object MapList extends SimplificationType {
  override def hint: String = ScalaInspectionBundle.message("replace.with.get")

  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    expr match {
      case qual `.lift` (n) if isMap(qual) =>
        Some(replace(expr).withText(invocationText(qual, "get", n)).highlightFrom(qual))
      case _ => None
    }
  }

}
