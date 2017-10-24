package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

/**
 * @author Nikolay.Tropin
 */
class ReverseTakeReverseInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] = Array(ReverseTakeReverse)
}

object ReverseTakeReverse extends SimplificationType {
  override def hint: String = InspectionBundle.message("replace.reverse.take.reverse.with.takeRight")

  override def getSimplification(expr: ScExpression): Option[Simplification] = expr match {
    case qual`.reverse`()`.take`(n)`.reverse`() =>
      Some(replace(expr).withText(invocationText(qual, "takeRight", n)).highlightFrom(qual))
    case _ => None
  }
}
