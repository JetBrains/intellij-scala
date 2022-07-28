package org.jetbrains.plugins.scala
package codeInspection
package collections

import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

import scala.collection.immutable.ArraySeq

class EqualityToSameElementsInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: ArraySeq[SimplificationType] =
    ArraySeq(ArrayEquality, IteratorsEquality)
}

object ArrayEquality extends SimplificationType {
  override def hint: String = ScalaInspectionBundle.message("replace.equals.with.sameElements")
  override def description: String = ScalaInspectionBundle.message("config.description.for.arrays")

  override def getSimplification(expr: ScExpression): Option[Simplification] = expr match {
    case left `==` right if arraysOrSeqAndArray(left, right) =>
      Some(replace(expr).withText(invocationText(left, "sameElements", right)).highlightRef)
    case left `!=` right if arraysOrSeqAndArray(left, right) =>
      Some(replace(expr).withText(invocationText(negation = true, left, "sameElements", right)).highlightRef)
    case _ => None
  }


  private def arraysOrSeqAndArray(left: ScExpression, right: ScExpression) = {
    isArray(left) && (isArray(right) || isSeq(right)) ||
            isArray(right) && isSeq(left)
  }
}

object IteratorsEquality extends SimplificationType {
  override def hint: String = ScalaInspectionBundle.message("replace.equals.with.sameElements")
  override def description: String = ScalaInspectionBundle.message("config.description.for.iterators")

  override def getSimplification(expr: ScExpression): Option[Simplification] = expr match {
    case left `==` right if iterators(left, right) =>
      Some(replace(expr).withText(invocationText(left, "sameElements", right)).highlightRef)
    case left `!=` right if iterators(left, right) =>
      Some(replace(expr).withText(invocationText(negation = true, left, "sameElements", right)).highlightRef)
    case _ => None
  }

  private def iterators(left: ScExpression, right: ScExpression) = isIterator(left) && isIterator(right)
}
