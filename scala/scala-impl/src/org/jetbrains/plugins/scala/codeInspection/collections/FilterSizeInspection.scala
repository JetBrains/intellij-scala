package org.jetbrains.plugins.scala
package codeInspection
package collections

import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

import scala.collection.immutable.ArraySeq

class FilterSizeInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: ArraySeq[SimplificationType] =
    ArraySeq(FilterSize)
}

object FilterSize extends SimplificationType {

  override def hint: String = ScalaInspectionBundle.message("filter.size.hint")

  override def getSimplification(expr: ScExpression): Option[Simplification] =
    expr match {
      // TODO infix notation?
      case `.sizeOrLength`(qual`.filter`(cond)) =>
        Some(replace(expr).withText(invocationText(qual, "count", cond)).highlightFrom(qual))
      case _ =>
        None
    }
}
