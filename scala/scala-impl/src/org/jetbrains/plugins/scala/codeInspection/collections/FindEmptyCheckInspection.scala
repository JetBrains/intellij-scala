package org.jetbrains.plugins.scala
package codeInspection
package collections

import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

import scala.collection.immutable.ArraySeq

class FindEmptyCheckInspection extends OperationOnCollectionInspection{
  override def possibleSimplificationTypes: ArraySeq[SimplificationType] =
    ArraySeq(FindIsDefined, FindIsEmpty)
}

object FindIsDefined extends SimplificationType() {
  override def hint: String = ScalaInspectionBundle.message("find.isDefined.hint")

  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    expr match {
      case CheckIsDefined(qual`.find`(cond), s, e) =>
        val start = Math.min(s, qual.end)
        val end = Math.max(e, expr.end)
        val existsText = invocationText(qual, "exists", cond)
        Some(replace(expr).withText(existsText).highlightRange(start, end))
      case _ => None
    }
  }
}

object FindIsEmpty extends SimplificationType() {
  override def hint: String = ScalaInspectionBundle.message("find.isEmpty.hint")

  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    expr match {
      case CheckIsEmpty(qual`.find`(cond), s, e) =>
        val start = Math.min(s, qual.end)
        val end = Math.max(e, expr.end)
        val notExistsText = invocationText(negation = true, qual, "exists", cond)
        Some(replace(expr).withText(notExistsText).highlightRange(start, end))
      case _ => None
    }
  }
}