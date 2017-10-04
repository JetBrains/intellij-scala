package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

/**
  * @author Nikolay.Tropin
  */

class ReplaceToWithUntilInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] =
    Array(ReplaceToWithUntil)
}

object ReplaceToWithUntil extends SimplificationType {

  def hint: String = InspectionBundle.message("replace.to.with.until")

  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    expr match {
      case qual`.to`(x `-` literal("1")) =>
        Some(replace(expr).withText(invocationText(qual, "until", x)).highlightFrom(qual))
      case _ => None
    }
  }
}