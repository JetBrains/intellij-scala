package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

import scala.collection.immutable.ArraySeq

class ReverseFindInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: ArraySeq[SimplificationType] =
    ArraySeq(ReverseFindInspection)
}

object ReverseFindInspection extends SimplificationType {

  override def hint: String = ScalaInspectionBundle.message("replace.with.findlast")

  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    expr match {
      case `.reverse`(qual)`.find`(f) =>
        Some(replace(expr).withText(invocationText(qual, "findLast", f)).highlightRange(qual.endOffset, expr.endOffset))
      case _ => None
    }
  }
}