package org.jetbrains.plugins.scala
package codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.codeInspection.collections.Utils._

/**
 * Nikolay.Tropin
 * 2014-05-05
 */
class FindIsDefinedInspection extends OperationOnCollectionInspection{
  override def possibleSimplificationTypes: Array[SimplificationType] =
    Array(new FindIsDefined(this))
}

class FindIsDefined(inspection: OperationOnCollectionInspection) extends SimplificationType(inspection) {
  def hint = InspectionBundle.message("find.isDefined.hint")

  def getSimplification(last: MethodRepr, second: MethodRepr): List[Simplification] = {
    (last.optionalMethodRef, second.optionalMethodRef) match {
      case (Some(lastRef), Some(secondRef))
        if lastRef.refName == "isDefined" &&
                secondRef.refName == "find" &&
                checkResolve(lastRef, likeOptionClasses) &&
                checkResolve(secondRef, likeCollectionClasses) =>

        createSimplification(second, last.itself, second.args, "exists")
      case _ => Nil
    }
  }
}