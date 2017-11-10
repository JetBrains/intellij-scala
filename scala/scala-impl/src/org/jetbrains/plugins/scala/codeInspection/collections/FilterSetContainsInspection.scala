package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

/**
  * @author t-kameyama
  */
class FilterSetContainsInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] = Array(FilterSetContains)
}

object FilterSetContains extends SimplificationType {

  override def hint: String = InspectionBundle.message("remove.redundant.contains")

  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    expr match {
      case qual `.filter` (set `.contains` ()) if isSet(set) =>
        val highlightStart = set.end + 1
        val highlightEnd = expr.end - 1
        Some(replace(expr).withText(invocationText(qual, "filter", set)).highlightRange(highlightStart, highlightEnd))
      case _ => None
    }
  }

}