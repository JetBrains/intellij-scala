package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

/**
 * @author Nikolay.Tropin
 */
class RedundantHeadOrLastOptionInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] = Array(RedundantHeadOption, RedundantLastOption)
}

object RedundantHeadOption extends SimplificationType {
  override def hint: String = InspectionBundle.message("remove.redundant.headOption")

  override def getSimplification(expr: ScExpression): Option[Simplification] = expr match {
    case qual`.headOption`() if isOption(qual) =>
      Some(replace(expr).withText(qual.getText).highlightFrom(qual))
    case _ => None
  }
}

object RedundantLastOption extends SimplificationType {
  override def hint: String = InspectionBundle.message("remove.redundant.lastOption")

  override def getSimplification(expr: ScExpression): Option[Simplification] = expr match {
    case qual`.lastOption`() if isOption(qual) =>
      Some(replace(expr).withText(qual.getText).highlightFrom(qual))
    case _ => None
  }
}