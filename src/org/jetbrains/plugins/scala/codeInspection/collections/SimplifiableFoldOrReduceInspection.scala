package org.jetbrains.plugins.scala
package codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.codeInspection.collections.OperationOnCollectionsUtil._
import org.jetbrains.plugins.scala.codeInspection.collections.SimplifiableFoldOrReduceInspection._
import scala.Some
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

/**
 * Nikolay.Tropin
 * 2014-05-05
 */
class SimplifiableFoldOrReduceInspection extends OperationOnCollectionInspection {
  val foldSum = new FoldSimplificationType(this, "fold.sum", "0", "+", "sum")
  val foldProduct = new FoldSimplificationType(this, "fold.product", "1", "*", "product")
  val reduceSum = new ReduceSimplificationType(this, "reduce.sum", "+", "sum")
  val reduceProduct = new ReduceSimplificationType(this,"reduce.product", "*", "product")
  val reduceMin = new ReduceSimplificationType(this, "reduce.min", "min", "min")
  val reduceMax = new ReduceSimplificationType(this, "reduce.max", "max", "max")

  override def possibleSimplificationTypes: Array[SimplificationType] =
    Array(foldSum, foldProduct, reduceSum, reduceProduct, reduceMax, reduceMin)
}

object SimplifiableFoldOrReduceInspection {

}

class FoldSimplificationType(inspection: OperationOnCollectionInspection,
                             keyPrefix: String,
                             startElem: String,
                             opName: String,
                             methodName: String) extends SimplificationType(inspection){

  override def getSimplification(last: MethodRepr, second: MethodRepr): List[Simplification] = {
    (last.optionalMethodRef, second.optionalMethodRef) match {
      case (None, Some(secondRef))
        if foldMethodNames.contains(secondRef.refName) &&
                isLiteral(second.args, startElem) &&
                last.args.size == 1 &&
                isBinaryOp(last.args(0), opName) &&
                checkHasImplicitParameterFor(methodName, second.optionalBase) &&
                checkResolve(secondRef, likeCollectionClasses) =>

        createSimplification(second, last.itself, Nil, methodName)
      case _ => Nil
    }
  }

  override def hint = InspectionBundle.message(keyPrefix + ".hint")
  override def description = InspectionBundle.message(keyPrefix + ".short")
}

class ReduceSimplificationType(inspection: OperationOnCollectionInspection,
                               keyPrefix: String,
                               opName: String,
                               methodName: String) extends SimplificationType(inspection) {

  override def getSimplification(last: MethodRepr): List[Simplification] = {
    last.optionalMethodRef match {
      case Some(ref)
        if reduceMethodNames.contains(ref.refName) &&
                last.args.size == 1 &&
                isBinaryOp(last.args(0), opName) &&
                checkResolve(ref, likeCollectionClasses) &&
                checkHasImplicitParameterFor(methodName, last.optionalBase) =>

        createSimplification(last, last.itself, Nil, methodName)
      case _ => Nil
    }
  }

  override def hint = InspectionBundle.message(keyPrefix + ".hint")
  override def description = InspectionBundle.message(keyPrefix + ".short")
}