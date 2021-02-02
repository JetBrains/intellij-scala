package org.jetbrains.plugins.scala
package codeInspection
package collections

import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

/**
  * mattfowler
  * 4/28/16
  */
class FindAndMapToGetInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] = Array(FindAndMapToApply)
}

object FindAndMapToApply extends SimplificationType {
  override def hint: String = ScalaInspectionBundle.message("replace.find.and.map.with.apply")

  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    expr match {
      case qual`.find`(`_._1`() `==` equalsRhsExpr)`.map`(`_._2`()) if isMap(qual) =>
        Some(replace(expr).withText(invocationText(qual, "get", equalsRhsExpr)).highlightFrom(qual))
      case _ => None
    }
  }
}