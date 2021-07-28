package org.jetbrains.plugins.scala
package codeInspection
package collections

import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

import scala.collection.immutable.ArraySeq

/**
 * @author Nikolay.Tropin
 */

object ReverseMapInspection extends SimplificationType() {
  override def hint: String = ScalaInspectionBundle.message("replace.reverse.map")

  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    expr match {
      case `.reverse`(qual) `.map` f =>
        Some(replace(expr).withText(invocationText(qual, "reverseMap", f)).highlightFrom(qual))
      case _ => None
    }
  }
}

class ReverseMapInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: ArraySeq[SimplificationType] = ArraySeq(ReverseMapInspection)
}