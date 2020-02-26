package org.jetbrains.plugins.scala
package codeInspection
package collections

import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

/**
 * @author Markus.Hauck
 */
class SortedMaxMinInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] =
    Array(SortedHeadIsMin, SortedLastIsMax, SortByHeadIsMinBy, SortByLastIsMaxBy)
}

object SortedHeadIsMin extends SimplificationType {
  override def hint: String = ScalaInspectionBundle.message("replace.sorted.head.with.min")

  override def getSimplification(expr: ScExpression): Option[Simplification] = expr match {
    case qual`.sorted`()`.head`() =>
      Some(replace(expr).withText(invocationText(qual, "min")).highlightFrom(qual))
    case _ => None
  }
}

object SortedLastIsMax extends SimplificationType {
  override def hint: String = ScalaInspectionBundle.message("replace.sorted.last.with.max")

  override def getSimplification(expr: ScExpression): Option[Simplification] = expr match {
    case qual`.sorted`()`.last`() =>
      Some(replace(expr).withText(invocationText(qual, "max")).highlightFrom(qual))
    case _ => None
  }
}

object SortByHeadIsMinBy extends SimplificationType {
  override def hint: String = ScalaInspectionBundle.message("replace.sortBy.head.with.minBy")

  override def getSimplification(expr: ScExpression): Option[Simplification] = expr match {
    case qual`.sortBy`(f)`.head`() =>
      Some(replace(expr).withText(invocationText(qual, "minBy", f)).highlightFrom(qual))
    case _ => None
  }
}

object SortByLastIsMaxBy extends SimplificationType {
  override def hint: String = ScalaInspectionBundle.message("replace.sortBy.last.with.maxBy")

  override def getSimplification(expr: ScExpression): Option[Simplification] = expr match {
    case qual`.sortBy`(f)`.last`() =>
      Some(replace(expr).withText(invocationText(qual, "maxBy", f)).highlightFrom(qual))
    case _ => None
  }
}
