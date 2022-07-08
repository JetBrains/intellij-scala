package org.jetbrains.plugins.scala
package codeInspection
package collections

import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

import scala.collection.immutable.ArraySeq

class FilterHeadOptionInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: ArraySeq[SimplificationType] =
    ArraySeq(FilterHeadOption)
}

object FilterHeadOption extends SimplificationType {

  override def hint: String = ScalaInspectionBundle.message("filter.headOption.hint")

  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    expr match {
      // TODO infix notation?
      case `.headOption`(qual`.filter`(cond)) if !hasSideEffects(cond) =>
        Some(replace(expr).withText(invocationText(qual, "find", cond)).highlightFrom(qual))
      case _ => None
    }
  }
}
