package org.jetbrains.plugins.scala
package codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.codeInspection.collections.OperationOnCollectionsUtil._

/**
 * Nikolay.Tropin
 * 2014-05-05
 */
class FindNotEqualsNoneInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] =
    Array(new FindNotEqualsNone(this))
}

class FindNotEqualsNone(inspection: OperationOnCollectionInspection) extends SimplificationType(inspection){

  def hint = InspectionBundle.message("find.notEquals.none.hint")

  override def getSimplification(last: MethodRepr, second: MethodRepr): List[Simplification] = {
    val lastArgs = last.args
    (last.optionalMethodRef, second.optionalMethodRef) match {
      case (Some(lastRef), Some(secondRef))
        if lastRef.refName == "!=" &&
                secondRef.refName == "find" &&
                lastArgs.size == 1 &&
                lastArgs(0).getText == "None" &&
                checkResolve(lastArgs(0), Array("scala.None")) &&
                checkResolve(secondRef, likeCollectionClasses) =>

        createSimplification(second, last.itself, "exists", second.args)
      case _ => Nil
    }
  }
}
