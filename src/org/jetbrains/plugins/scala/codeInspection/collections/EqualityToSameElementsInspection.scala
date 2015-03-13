package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

/**
 * @author Nikolay.Tropin
 */
class EqualityToSameElementsInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] =
    Array(ArrayEquality, IteratorsEquality)
}

object ArrayEquality extends SimplificationType {
  override def hint: String = InspectionBundle.message("replace.equals.with.sameElements")
  override def description: String = "For arrays"

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
  override def hint: String = InspectionBundle.message("replace.equals.with.sameElements")
  override def description: String = "For iterators"

  override def getSimplification(expr: ScExpression): Option[Simplification] = expr match {
    case left `==` right if iterators(left, right) =>
      Some(replace(expr).withText(invocationText(left, "sameElements", right)).highlightRef)
    case left `!=` right if iterators(left, right) =>
      Some(replace(expr).withText(invocationText(negation = true, left, "sameElements", right)).highlightRef)
    case _ => None
  }

  private def iterators(left: ScExpression, right: ScExpression) = isIterator(left) && isIterator(right)
}
