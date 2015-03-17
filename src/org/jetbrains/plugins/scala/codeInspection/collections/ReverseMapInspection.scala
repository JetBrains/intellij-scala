package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.codeInspection.collections.OperationOnCollectionsUtil._

/**
 * @author Nikolay.Tropin
 */

class ReverseMap(inspection: OperationOnCollectionInspection) extends SimplificationType(inspection) {
  override def hint: String = InspectionBundle.message("replace.reverse.map")

  override def getSimplification(last: MethodRepr, second: MethodRepr): List[Simplification] = {
    (last.optionalMethodRef, second.optionalMethodRef) match {
      case (Some(lastRef), Some(secondRef))
        if lastRef.refName == "map" && secondRef.refName == "reverse" &&
                checkResolve(lastRef, likeCollectionClasses) && checkResolve(secondRef, likeCollectionClasses) =>
        createSimplification(second, last.itself, "reverseMap", last.args)
      case _ => Nil
    }
  }
}

class ReverseMapInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] = Array(new ReverseMap(this))
}