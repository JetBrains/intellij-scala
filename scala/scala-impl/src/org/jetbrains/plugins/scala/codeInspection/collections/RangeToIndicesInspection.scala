package org.jetbrains.plugins.scala
package codeInspection
package collections

import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

import scala.collection.immutable.ArraySeq

class RangeToIndicesInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: ArraySeq[SimplificationType] = ArraySeq(RangeToIndices, UntilToIndices, ToToIndices)
}

object RangeToIndices extends SimplificationType {
  override def hint: String = ScalaInspectionBundle.message("hint.replace.with.indices")
  //noinspection ScalaExtractStringToBundle
  override def description: String = "Range(0, seq.size)"

  private val Range = invocation("apply").from(ArraySeq("scala.collection.immutable.Range"))

  override def getSimplification(expr: ScExpression): Option[Simplification] = expr match {
    // TODO infix notation?
    case Range(_, literal("0"), `.sizeOrLength`(qual)) if isSeq(qual) || isArray(qual) => toIndicesSimplification(expr, qual)
    case _ => None
  }

  def toIndicesSimplification(expr: ScExpression, qual: ScExpression): Some[Simplification] = {
    Some(replace(expr)
      .withText(invocationText(qual, "indices"))
      .withHint(ScalaInspectionBundle.message("hint.replace.with.indices.with.preview", qual.getText))
      .highlightAll
    )
  }
}

object UntilToIndices extends SimplificationType {
  //noinspection ScalaExtractStringToBundle
  override def hint: String = "0 until seq.size"

  private val `.until` = invocation("until").from(ArraySeq("scala.runtime.RichInt"))

  override def getSimplification(expr: ScExpression): Option[Simplification] = expr match {
    // TODO infix notation?
    case literal("0")`.until`(`.sizeOrLength`(qual))  if isSeq(qual) || isArray(qual) =>
      RangeToIndices.toIndicesSimplification(expr, qual)
    case _ => None
  }

}

object ToToIndices extends SimplificationType {
  //noinspection ScalaExtractStringToBundle
  override def hint: String = "0 to (seq.size - 1)"

  private val `.to` = invocation("to").from(ArraySeq("scala.runtime.RichInt"))

  override def getSimplification(expr: ScExpression): Option[Simplification] = expr match {
    case literal("0")`.to`(`.sizeOrLength`(qual) `-` literal("1"))  if isSeq(qual) || isArray(qual) =>
      RangeToIndices.toIndicesSimplification(expr, qual)
    case _ => None
  }
}
