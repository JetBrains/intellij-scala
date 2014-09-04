package org.jetbrains.plugins.scala
package codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.codeInspection.collections.OperationOnCollectionsUtil._

/**
 * Nikolay.Tropin
 * 2014-05-06
 */
class GetOrElseNullInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] =
    Array(new GetOrElseNull(this))
}

class GetOrElseNull(inspection: OperationOnCollectionInspection) extends SimplificationType(inspection) {
  override def getSimplification(single: MethodRepr) = {
    single.itself match {
      case MethodRepr(itself, Some(base), Some(ref), args)
        if ref.refName == "getOrElse" &&
                isLiteral(args, text = "null") &&
                checkResolve(ref, likeOptionClasses) =>

        createSimplification(single, itself, "orNull", Nil)
      case _ => Nil
    }
  }

  override def hint = InspectionBundle.message("getOrElse.null.hint")
}
