package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

/**
 * @author Nikolay.Tropin
 */
class ZeroIndexToHeadInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] = Array(ZeroIndexToHead)
}

object ZeroIndexToHead extends SimplificationType() {
  override def hint: String = InspectionBundle.message("replace.with.head")

  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    expr match {
      case qual`.apply`(literal("0")) if isSeq(qual) =>
        Some(replace(expr).withText(invocationText(qual, "head")).highlightFrom(qual))
      case _ => None
    }
  }
}
