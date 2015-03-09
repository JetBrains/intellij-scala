package org.jetbrains.plugins.scala
package codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

/**
 * Nikolay.Tropin
 * 2014-05-05
 */
class FoldTrueAndInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] =
    Array(FoldTrueAnd)
}

object FoldTrueAnd extends SimplificationType(){

  def hint = InspectionBundle.message("fold.true.and.hint")

  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    expr match {
      case qual`.foldLeft`(literal("true"), andCondition(cond)) if hasSideEffects(cond) =>
        None
      case qual`.fold`(literal("true"), andCondition(cond)) =>
        Some(replace(expr).withText(invocationText(qual, "forall", cond)).highlightFrom(qual))
      case _ => None
    }
  }
}
