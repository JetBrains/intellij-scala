package org.jetbrains.plugins.scala
package codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.codeInspection.collections.OperationOnCollectionsUtil._

/**
 * Nikolay.Tropin
 * 2014-05-05
 */
class MapGetOrElseFalseInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] =
    Array(new MapGetOrElseFalse(this))
}

class MapGetOrElseFalse(inspection: OperationOnCollectionInspection) extends SimplificationType(inspection) {
  def hint = InspectionBundle.message("map.getOrElse.false.hint")
  override def getSimplification(last: MethodRepr, second: MethodRepr): List[Simplification] = {
    val (lastArgs, secondArgs) = (last.args, second.args)
    (last.optionalMethodRef, second.optionalMethodRef) match {
      case (Some(lastRef), Some(secondRef))
        if lastRef.refName == "getOrElse" &&
                secondRef.refName == "map" &&
                isLiteral(lastArgs, text = "false") &&
                secondArgs.size == 1 &&
                isFunctionWithBooleanReturn(secondArgs(0)) &&
                checkResolve(lastRef, likeOptionClasses) &&
                checkResolve(secondRef, likeOptionClasses) =>

        createSimplification(second, last.itself, second.args, "exists")
      case _ => Nil
    }
  }
}
