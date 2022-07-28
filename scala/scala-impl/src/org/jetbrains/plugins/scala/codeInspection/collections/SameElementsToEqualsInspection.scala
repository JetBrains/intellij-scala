package org.jetbrains.plugins.scala
package codeInspection
package collections

import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

import scala.collection.immutable.ArraySeq

class SameElementsToEqualsInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: ArraySeq[SimplificationType] =
    ArraySeq(SameElementsToEquals, CorrespondsToEquals)
}

object SameElementsToEquals extends SimplificationType {
  override def hint: String = ScalaInspectionBundle.message("replace.sameElements.with.equals")

  override def getSimplification(expr: ScExpression): Option[Simplification] = expr match {
    case left`.sameElements`(right) if isOfSameKind(left, right) && !bothSortedSetsOrMaps(left, right) =>
      Some(replace(expr).withText(s"${left.getText} == ${right.getText}").highlightRef)
    case _ => None
  }

  private def isOfSameKind(left: ScExpression, right: ScExpression) = {
    val leftWithoutConversions = withoutConversions(left)
    val rightWithoutConversions = withoutConversions(right)
    isSet(leftWithoutConversions) && isSet(rightWithoutConversions) ||
            isSeq(leftWithoutConversions) && isSeq(rightWithoutConversions) ||
            isMap(leftWithoutConversions) && isMap(rightWithoutConversions)
  }

  private def bothSortedSetsOrMaps(left: ScExpression, right: ScExpression) = {
    isSortedSet(left) && isSortedSet(right) || isSortedMap(left) && isSortedMap(right)
  }
}

object CorrespondsToEquals extends SimplificationType {
  override def hint: String = ScalaInspectionBundle.message("replace.corresponds.with.equals")

  override def getSimplification(expr: ScExpression): Option[Simplification] = expr match {
    case left`.corresponds`(right, binaryOperation("==")) if isSeq(left) && isSeq(right) =>
      Some(replace(expr).withText(s"${left.getText} == ${right.getText}").highlightRef)
    case _ => None
  }
}


