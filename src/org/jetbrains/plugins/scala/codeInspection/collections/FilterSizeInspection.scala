package org.jetbrains.plugins.scala
package codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.codeInspection.collections.Utils._

/**
 * Nikolay.Tropin
 * 2014-05-05
 */
class FilterSizeInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] =
    Array(new FilterSize(this))
}

class FilterSize(inspection: OperationOnCollectionInspection) extends SimplificationType(inspection) {

  def hint = InspectionBundle.message("filter.size.hint")

  override def getSimplification(last: MethodRepr, second: MethodRepr): List[Simplification] = {
    (last.optionalMethodRef, second.optionalMethodRef) match {
      case (Some(lastRef), Some(secondRef)) if List("size", "length").contains(lastRef.refName) &&
              secondRef.refName == "filter" &&
              checkResolve(lastRef, likeCollectionClasses) &&
              checkResolve(secondRef, likeCollectionClasses) =>
        createSimplification(second, last.itself, second.args, "count")
      case _ => Nil
    }
  }
}
