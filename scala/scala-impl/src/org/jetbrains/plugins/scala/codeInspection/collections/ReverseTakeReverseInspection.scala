package org.jetbrains.plugins.scala
package codeInspection
package collections

import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

/**
 * @author Nikolay.Tropin
 */
class ReverseTakeReverseInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] = Array(ReverseTakeReverse)
}

object ReverseTakeReverse extends SimplificationType {
  override def hint: String = ScalaInspectionBundle.message("replace.reverse.take.reverse.with.takeRight")

  override def getSimplification(expr: ScExpression): Option[Simplification] = expr match {
    // TODO infix notation?
    case `.reverse`(`.reverse`(qual)`.take`(n)) =>
      Some(replace(expr).withText(invocationText(qual, "takeRight", n)).highlightFrom(qual))
    case _ => None
  }
}
