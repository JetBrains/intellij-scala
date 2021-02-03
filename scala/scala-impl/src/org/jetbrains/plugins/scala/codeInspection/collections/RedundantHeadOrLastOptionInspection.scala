package org.jetbrains.plugins.scala
package codeInspection
package collections

import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

import scala.collection.immutable.ArraySeq

/**
 * @author Nikolay.Tropin
 */
class RedundantHeadOrLastOptionInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: ArraySeq[SimplificationType] = ArraySeq(RedundantHeadOption, RedundantLastOption)
}

object RedundantHeadOption extends SimplificationType {
  override def hint: String = ScalaInspectionBundle.message("remove.redundant.headOption")

  override def getSimplification(expr: ScExpression): Option[Simplification] = expr match {
    // TODO infix notation?
    case `.headOption`(qual) if isOption(qual) =>
      Some(replace(expr).withText(qual.getText).highlightFrom(qual))
    case _ => None
  }
}

object RedundantLastOption extends SimplificationType {
  override def hint: String = ScalaInspectionBundle.message("remove.redundant.lastOption")

  override def getSimplification(expr: ScExpression): Option[Simplification] = expr match {
    // TODO infix notation?
    case `.lastOption`(qual) if isOption(qual) =>
      Some(replace(expr).withText(qual.getText).highlightFrom(qual))
    case _ => None
  }
}