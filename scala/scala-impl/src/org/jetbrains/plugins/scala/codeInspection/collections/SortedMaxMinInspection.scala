package org.jetbrains.plugins.scala
package codeInspection
package collections

import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

import scala.collection.immutable.ArraySeq

class SortedMaxMinInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: ArraySeq[SimplificationType] =
    ArraySeq(SortedHeadIsMin, SortedLastIsMax, SortByHeadIsMinBy, SortByLastIsMaxBy)
}

object SortedHeadIsMin extends SimplificationType {
  override def hint: String = ScalaInspectionBundle.message("replace.sorted.head.with.min")

  override def getSimplification(expr: ScExpression): Option[Simplification] = expr match {
    // TODO infix notation?
    case `.head`(`.sorted`(qual)) =>
      Some(replace(expr).withText(invocationText(qual, "min")).highlightFrom(qual))
    case _ => None
  }
}

object SortedLastIsMax extends SimplificationType {
  override def hint: String = ScalaInspectionBundle.message("replace.sorted.last.with.max")

  override def getSimplification(expr: ScExpression): Option[Simplification] = expr match {
    // TODO infix notation?
    case `.last`(`.sorted`(qual)) =>
      Some(replace(expr).withText(invocationText(qual, "max")).highlightFrom(qual))
    case _ => None
  }
}

object SortByHeadIsMinBy extends SimplificationType {
  override def hint: String = ScalaInspectionBundle.message("replace.sortBy.head.with.minBy")

  override def getSimplification(expr: ScExpression): Option[Simplification] = expr match {
    // TODO infix notation?
    case `.head`(`.sortBy`(qual, f)) =>
      Some(replace(expr).withText(invocationText(qual, "minBy", f)).highlightFrom(qual))
    case _ => None
  }
}

object SortByLastIsMaxBy extends SimplificationType {
  override def hint: String = ScalaInspectionBundle.message("replace.sortBy.last.with.maxBy")

  override def getSimplification(expr: ScExpression): Option[Simplification] = expr match {
    // TODO infix notation?
    case `.last`(`.sortBy`(qual, f)) =>
      Some(replace(expr).withText(invocationText(qual, "maxBy", f)).highlightFrom(qual))
    case _ => None
  }
}
