package org.jetbrains.plugins.scala
package codeInspection
package collections

import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

import scala.collection.immutable.ArraySeq

class FoldTrueAndInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: ArraySeq[SimplificationType] =
    ArraySeq(FoldTrueAnd)
}

object FoldTrueAnd extends SimplificationType(){

  override def hint: String = ScalaInspectionBundle.message("fold.true.and.hint")

  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    expr match {
      case _ `.foldLeft`(literal("true"), andCondition(cond)) if hasSideEffects(cond) =>
        None
      case qual`.fold`(literal("true"), andCondition(cond)) =>
        Some(replace(expr).withText(invocationText(qual, "forall", cond)).highlightFrom(qual))
      case _ => None
    }
  }
}
