package org.jetbrains.plugins.scala
package codeInspection
package collections

import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

import scala.collection.immutable.ArraySeq

class SimplifiableFoldOrReduceInspection extends OperationOnCollectionInspection {
  val foldSum: FoldSimplificationType = new FoldSimplificationType(this, "0", "+", "sum") {
    override def hint: String = ScalaInspectionBundle.message("fold.sum.hint")
    override def description: String = ScalaInspectionBundle.message("fold.sum.short")
  }
  val foldProduct: FoldSimplificationType = new FoldSimplificationType(this, "1", "*", "product") {
    override def hint: String = ScalaInspectionBundle.message("fold.product.hint")
    override def description: String = ScalaInspectionBundle.message("fold.product.short")
  }
  val reduceSum: ReduceSimplificationType = new ReduceSimplificationType(this, "+", "sum") {
    override def hint: String = ScalaInspectionBundle.message("reduce.sum.hint")
    override def description: String = ScalaInspectionBundle.message("reduce.sum.short")
  }
  val reduceProduct: ReduceSimplificationType = new ReduceSimplificationType(this,"*", "product") {
    override def hint: String = ScalaInspectionBundle.message("reduce.product.hint")
    override def description: String = ScalaInspectionBundle.message("reduce.product.short")
  }
  val reduceMin: ReduceSimplificationType = new ReduceSimplificationType(this, "min", "min") {
    override def hint: String = ScalaInspectionBundle.message("reduce.min.hint")
    override def description: String = ScalaInspectionBundle.message("reduce.min.short")
  }
  val reduceMax: ReduceSimplificationType = new ReduceSimplificationType(this, "max", "max") {
    override def hint: String = ScalaInspectionBundle.message("reduce.max.hint")
    override def description: String = ScalaInspectionBundle.message("reduce.max.short")
  }

  override def possibleSimplificationTypes: ArraySeq[SimplificationType] =
    ArraySeq(foldSum, foldProduct, reduceSum, reduceProduct, reduceMax, reduceMin)
}

object SimplifiableFoldOrReduceInspection {

}

abstract class FoldSimplificationType(inspection: OperationOnCollectionInspection,
                             startElem: String,
                             opName: String,
                             methodName: String) extends SimplificationType {

  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    expr match {
      case qual`.fold`(literal(`startElem`), binaryOperation(`opName`)) if implicitParameterExistsFor(methodName, qual) =>
        val simpl = replace(expr).withText(invocationText(qual, methodName)).highlightFrom(qual)
        Some(simpl)
      case _ => None
    }
  }
}

abstract class ReduceSimplificationType(inspection: OperationOnCollectionInspection,
                               opName: String,
                               methodName: String) extends SimplificationType {
  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    expr match {
      case qual`.reduce`(binaryOperation(`opName`)) if implicitParameterExistsFor(methodName, qual) =>
        val simpl = replace(expr).withText(invocationText(qual, methodName)).highlightFrom(qual)
        Some(simpl)
      case _ => None
    }
  }
}