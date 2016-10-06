package org.jetbrains.plugins.scala
package codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

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
                             methodName: String) extends SimplificationType(){

  override def hint: String = InspectionBundle.message(keyPrefix + ".hint")
  override def description: String = InspectionBundle.message(keyPrefix + ".short")

  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    expr match {
      case qual`.fold`(literal(`startElem`), binaryOperation(`opName`)) if implicitParameterExistsFor(methodName, qual) =>
        val simpl = replace(expr).withText(invocationText(qual, methodName)).highlightFrom(qual)
        Some(simpl)
      case _ => None
    }
  }
}

class ReduceSimplificationType(inspection: OperationOnCollectionInspection,
                               keyPrefix: String,
                               opName: String,
                               methodName: String) extends SimplificationType() {

  override def hint: String = InspectionBundle.message(keyPrefix + ".hint")
  override def description: String = InspectionBundle.message(keyPrefix + ".short")

  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    expr match {
      case qual`.reduce`(binaryOperation(`opName`)) if qual != null && implicitParameterExistsFor(methodName, qual) =>
        val simpl = replace(expr).withText(invocationText(qual, methodName)).highlightFrom(qual)
        Some(simpl)
      case _ => None
    }
  }
}