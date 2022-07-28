package org.jetbrains.plugins.scala
package codeInspection
package collections

import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

import scala.collection.immutable.ArraySeq

class GetGetOrElseInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: ArraySeq[SimplificationType] =
    ArraySeq(GetGetOrElse)
}

object GetGetOrElse extends SimplificationType() {

  override def hint: String = ScalaInspectionBundle.message("get.getOrElse.hint")

  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    expr match {
      case map`.getOnMap`(key)`.getOrElse`(default) =>
        Some(replace(expr).withText(invocationText(map, "getOrElse", key, default)).highlightFrom(map))
      case _ => None
    }
  }

}
