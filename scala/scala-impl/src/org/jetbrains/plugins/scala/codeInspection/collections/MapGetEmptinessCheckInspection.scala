package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

import scala.collection.immutable.ArraySeq

class MapGetEmptinessCheckInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: ArraySeq[SimplificationType] =
    ArraySeq(MapGetNonEmpty, MapGetIsEmpty)
}

object MapGetNonEmpty extends SimplificationType() {

  override def hint: String = ScalaInspectionBundle.message("replace.get.nonEmpty.with.contains")

  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    expr match {
      // TODO infix notation?
      case `.isDefined`(map`.getOnMap`(key)) =>
        Some(replace(expr)
          .withText(invocationText(negation = false, map, "contains", key))
          .highlightFrom(map))
      case _ => None
    }
  }
}

object MapGetIsEmpty extends SimplificationType() {

  override def hint: String = ScalaInspectionBundle.message("replace.get.isEmpty.with.not.contains")

  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    expr match {
      // TODO infix notation?
      case `.isEmpty`(map`.getOnMap`(key)) =>
        Some(replace(expr)
          .withText(invocationText(negation = true, map, "contains", key))
          .highlightFrom(map))
      case _ => None
    }
  }
}