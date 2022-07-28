package org.jetbrains.plugins.scala
package codeInspection
package collections

import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

import scala.collection.immutable.ArraySeq

class ZeroIndexToHeadInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: ArraySeq[SimplificationType] = ArraySeq(ZeroIndexToHead)
}

object ZeroIndexToHead extends SimplificationType() {
  override def hint: String = ScalaInspectionBundle.message("replace.with.head")

  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    expr match {
      case qual`.apply`(literal("0")) if isSeq(qual) && !isIndexedSeq(qual) =>
        Some(replace(expr).withText(invocationText(qual, "head")).highlightFrom(qual))
      case _ => None
    }
  }
}
