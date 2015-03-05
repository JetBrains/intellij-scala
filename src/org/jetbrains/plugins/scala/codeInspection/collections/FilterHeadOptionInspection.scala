package org.jetbrains.plugins.scala
package codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.codeInspection.collections.OperationOnCollectionsUtil._

/**
 * Nikolay.Tropin
 * 2014-05-05
 */
class FilterHeadOptionInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] =
    Array(FilterHeadOption)
}

object FilterHeadOption extends SimplificationType {

  def hint = InspectionBundle.message("filter.headOption.hint")

  override def getSimplification(last: MethodRepr, second: MethodRepr): List[Simplification] = {
    (last.optionalMethodRef, second.optionalMethodRef) match {
      case (Some(lastRef), Some(secondRef))
        if lastRef.refName == "headOption" &&
                secondRef.refName == "filter" &&
                checkResolve(lastRef, likeCollectionClasses) &&
                checkResolve(secondRef, likeCollectionClasses) =>

        createSimplification(second, last.itself, "find", second.args)
      case _ => Nil
    }
  }
}
