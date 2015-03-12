package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

/**
 * @author Nikolay.Tropin
 */
class MapToBooleanContainsInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] =
    Array(MapContainsFalse, MapContainsTrue)
}

object MapContainsFalse extends SimplificationType {
  override def hint: String = InspectionBundle.message("replace.map.contains.false.with.not.forall")
  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    expr match {
      case qual`.map`(pred @ returnsBoolean())`.contains`(literal("false")) =>
        val notForallText = invocationText(negation = true, qual, "forall", pred)
        Some(replace(expr).withText(notForallText).highlightFrom(qual))
      case _ => None
    }
  }
}

object MapContainsTrue extends SimplificationType {
  override def hint: String = InspectionBundle.message("replace.map.contains.true.with.exists")
  override def getSimplification(expr: ScExpression): Option[Simplification] = expr match {
    case qual`.map`(pred @ returnsBoolean())`.contains`(literal("true")) =>
      val existsText = invocationText(qual, "exists", pred)
      Some(replace(expr).withText(existsText).highlightFrom(qual))
    case _ => None
  }
}