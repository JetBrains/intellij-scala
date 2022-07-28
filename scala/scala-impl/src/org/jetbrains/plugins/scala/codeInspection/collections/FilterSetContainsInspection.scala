package org.jetbrains.plugins.scala
package codeInspection
package collections

import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

import scala.collection.immutable.ArraySeq

class FilterSetContainsInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: ArraySeq[SimplificationType] = ArraySeq(FilterSetContainsInspection)
}

object FilterSetContainsInspection extends SimplificationType {

  override def hint: String = ScalaInspectionBundle.message("remove.redundant.contains")

  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    expr match {
      // TODO infix notation?
      case qual `.filter` (`.contains`(set)) if isSet(set) =>
        val highlightStart = set.end + 1
        val highlightEnd = expr.end - 1
        Some(replace(expr).withText(invocationText(qual, "filter", set)).highlightRange(highlightStart, highlightEnd))
      case _ => None
    }
  }

}