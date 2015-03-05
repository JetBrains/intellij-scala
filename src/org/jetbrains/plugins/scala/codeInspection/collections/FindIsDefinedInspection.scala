package org.jetbrains.plugins.scala
package codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.codeInspection.collections.OperationOnCollectionsUtil._

/**
 * Nikolay.Tropin
 * 2014-05-05
 */
class FindIsDefinedInspection extends OperationOnCollectionInspection{
  override def possibleSimplificationTypes: Array[SimplificationType] =
    Array(FindIsDefined)
}

object FindIsDefined extends SimplificationType() {
  def hint = InspectionBundle.message("find.isDefined.hint")

  override def getSimplification(last: MethodRepr, second: MethodRepr): List[Simplification] = {
    (last.optionalMethodRef, second.optionalMethodRef) match {
      case (Some(lastRef), Some(secondRef))
        if lastRef.refName == "isDefined" &&
                secondRef.refName == "find" &&
                checkResolve(lastRef, likeOptionClasses) &&
                checkResolve(secondRef, likeCollectionClasses) =>

        createSimplification(second, last.itself, "exists", second.args)
      case _ => Nil
    }
  }
}