package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

/**
  * mattfowler
  * 4/28/16
  */
class FindAndMapToApplyInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] = Array(FindAndMapToApply)
}

object FindAndMapToApply extends SimplificationType {
  def hint = "Replace find and map with apply"

  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    expr match {
      case qual`.find`(underscore() `==` equalsRhsExpr)`.map`(`_._2`()) if isMap(qual) =>
        Some(replace(expr).withText(qual.getText + argListText(Seq(equalsRhsExpr))).highlightFrom(qual))
      case _ => None
    }
  }
}