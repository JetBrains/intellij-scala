package org.jetbrains.plugins.scala
package codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.codeInspection.collections.Utils._

/**
 * Nikolay.Tropin
 * 2014-05-05
 */
class SortFilterInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] =
    Array(new SortFilter(this))
}

class SortFilter(inspection: OperationOnCollectionInspection) extends SimplificationType(inspection) {

  def hint = InspectionBundle.message("sort.filter.hint")

  override def getSimplification(last: MethodRepr, second: MethodRepr): List[Simplification] = {
    (last.optionalMethodRef, second.optionalMethodRef) match {
      case (Some(lastRef), Some(secondRef))
        if lastRef.refName == "filter" &&
                List("sortWith", "sortBy", "sorted").contains(secondRef.refName) &&
                checkResolve(lastRef, likeCollectionClasses) &&
                checkResolve(secondRef, likeCollectionClasses) =>
        swapMethodsSimplification(last, second)
      case _ => Nil
    }
  }
}