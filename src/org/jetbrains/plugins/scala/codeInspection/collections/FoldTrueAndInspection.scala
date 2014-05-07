package org.jetbrains.plugins.scala
package codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.codeInspection.collections.OperationOnCollectionsUtil._

/**
 * Nikolay.Tropin
 * 2014-05-05
 */
class FoldTrueAndInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] =
    Array(new FoldTrueAnd(this))
}

class FoldTrueAnd(inspection: OperationOnCollectionInspection) extends SimplificationType(inspection){

  def hint = InspectionBundle.message("fold.true.and.hint")

  override def getSimplification(last: MethodRepr, second: MethodRepr): List[Simplification] = {
    (last.optionalMethodRef, second.optionalMethodRef) match {
      case (None, Some(secondRef))
        if foldMethodNames.contains(secondRef.refName) &&
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
