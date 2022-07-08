package org.jetbrains.plugins.scala
package codeInspection
package collections

import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

import scala.collection.immutable.ArraySeq

class ReplaceToWithUntilInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: ArraySeq[SimplificationType] =
    ArraySeq(ReplaceToWithUntil)
}

object ReplaceToWithUntil extends SimplificationType {

  override def hint: String = ScalaInspectionBundle.message("replace.to.with.until")

  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    expr match {
      case qual`.to`(x `-` literal("1")) =>
        Some(replace(expr).withText(invocationText(qual, "until", x)).highlightFrom(qual))
      case _ => None
    }
  }
}