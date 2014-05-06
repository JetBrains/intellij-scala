package org.jetbrains.plugins.scala
package codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.codeInspection.collections.Utils._

/**
 * Nikolay.Tropin
 * 2014-05-05
 */
class FoldLeftTrueAndInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] =
    Array(new FoldLeftTrueAnd(this))
}

class FoldLeftTrueAnd(inspection: OperationOnCollectionInspection) extends SimplificationType(inspection){

  def hint = InspectionBundle.message("foldLeft.true.and.hint")

  override def getSimplification(last: MethodRepr, second: MethodRepr): List[Simplification] = {
    (last.optionalMethodRef, second.optionalMethodRef) match {
      case (None, Some(secondRef))
        if List("foldLeft", "/:").contains(secondRef.refName) &&
                isLiteral(second.args, "true") &&
                last.args.size == 1 &&
                checkResolve(secondRef, likeCollectionClasses) =>

        andWithSomeFunction(last.args(0)).toList.flatMap { fun =>
          createSimplification(second, last.itself, Seq(fun), "forall")
        }
      case _ => Nil
    }
  }
}
