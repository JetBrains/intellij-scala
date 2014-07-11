package org.jetbrains.plugins.scala
package codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.codeInspection.collections.OperationOnCollectionsUtil._
import scala.Some

/**
 * Nikolay.Tropin
 * 2014-05-07
 */
class NotIsEmptyInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] =
    Array(new NotIsEmpty(this))
}

class NotIsEmpty(inspection: OperationOnCollectionInspection) extends SimplificationType(inspection) {
  def hint = InspectionBundle.message("not.isEmpty.hint")

  override def getSimplification(last: MethodRepr, second: MethodRepr) = {
    (last.optionalMethodRef, second.optionalMethodRef) match {
      case (Some(lastRef), Some(secondRef)) if lastRef.refName == "!" &&
              secondRef.refName == "isEmpty"  =>
        if (checkResolve(secondRef, likeOptionClasses))
          createSimplification(second, last.itself, "isDefined", Seq.empty)
        else if (checkResolve(secondRef, likeCollectionClasses))
          createSimplification(second, last.itself, "nonEmpty", Seq.empty)
        else Nil
      case _ => Nil
    }
  }
}