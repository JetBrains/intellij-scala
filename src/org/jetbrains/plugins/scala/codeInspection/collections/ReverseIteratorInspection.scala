package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.codeInspection.collections.OperationOnCollectionsUtil._

/**
 * @author Nikolay.Tropin
 */
class ReverseIterator(inspection: OperationOnCollectionInspection) extends SimplificationType(inspection) {
  override def hint: String = InspectionBundle.message("replace.reverse.iterator")

  override def getSimplification(last: MethodRepr, second: MethodRepr): List[Simplification] = {
    (last.optionalMethodRef, second.optionalMethodRef) match {
      case (Some(lastRef), Some(secondRef))
        if lastRef.refName == "iterator" && secondRef.refName == "reverse" &&
                checkResolve(lastRef, likeCollectionClasses) && checkResolve(secondRef, likeCollectionClasses) =>
        createSimplification(second, last.itself, "reverseIterator", last.args)
      case _ => Nil
    }
  }
}

class ReverseIteratorInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] = Array(new ReverseIterator(this))
}
