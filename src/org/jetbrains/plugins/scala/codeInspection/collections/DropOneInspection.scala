package org.jetbrains.plugins.scala
package codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.collections.OperationOnCollectionsUtil._
import scala.Some
import org.jetbrains.plugins.scala.codeInspection.InspectionBundle

/**
 * Nikolay.Tropin
 * 2014-05-07
 */
class DropOneInspection  extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] =
    Array(new DropOne(this))
}

class DropOne(inspection: OperationOnCollectionInspection) extends SimplificationType(inspection) {
  override def getSimplification(single: MethodRepr) = {
    single.itself match {
      case MethodRepr(_, _, Some(ref), args)
        if ref.refName == "drop" && isLiteral(args, "1") && checkResolve(ref, likeCollectionClasses) =>

        createSimplification(single, single.itself, Seq.empty, "tail")
      case _ => Nil
    }
  }

  override def hint = InspectionBundle.message("drop.one.hint")
}
