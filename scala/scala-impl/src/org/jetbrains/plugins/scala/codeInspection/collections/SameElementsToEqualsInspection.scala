package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

/**
 * @author Nikolay.Tropin
 */
class SameElementsToEqualsInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] =
    Array(SameElementsToEquals, CorrespondsToEquals)
}

object SameElementsToEquals extends SimplificationType {
  override def hint: String = InspectionBundle.message("replace.sameElements.with.equals")

  override def getSimplification(expr: ScExpression): Option[Simplification] = expr match {
    case left`.sameElements`(right) if isOfSameKind(left, right) && !bothSortedSetsOrMaps(left, right) =>
      Some(replace(expr).withText(s"${left.getText} == ${right.getText}").highlightRef)
    case _ => None
  }

  private def isOfSameKind(left: ScExpression, right: ScExpression) = {
    isSet(left) && isSet(right) ||
            isSeq(left) && isSeq(right) ||
            isMap(left) && isMap(right)
  }

  private def bothSortedSetsOrMaps(left: ScExpression, right: ScExpression) = {
    isSortedSet(left) && isSortedSet(right) || isSortedMap(left) && isSortedMap(right)
  }
}

object CorrespondsToEquals extends SimplificationType {
  override def hint: String = InspectionBundle.message("replace.corresponds.with.equals")

  override def getSimplification(expr: ScExpression): Option[Simplification] = expr match {
    case left`.corresponds`(right, binaryOperation("==")) if isSeq(left) && isSeq(right) =>
      Some(replace(expr).withText(s"${left.getText} == ${right.getText}").highlightRef)
    case _ => None
  }
}


