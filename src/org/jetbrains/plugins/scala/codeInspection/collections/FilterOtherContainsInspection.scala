package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.extensions.ResolvesTo
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScFunctionExpr, ScUnderscoreSection}

/**
 * @author Nikolay.Tropin
 */
class FilterOtherContainsInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] =
    Array(FilterContainsToIntersect, FilterNotContainsToDiff)
}

object `.contains _` {
  def unapply(expr: ScExpression): Option[ScExpression] = {
    stripped(expr) match {
      case ScFunctionExpr(Seq(x), Some(result)) =>
        stripped(result) match {
          case qual`.contains`(stripped(ResolvesTo(`x`))) if isIndependentOf(qual, x) => Some(qual)
          case _ => None
        }
      case qual`.contains`(underscore()) => Some(qual)
      case undSect: ScUnderscoreSection =>
        undSect.bindingExpr match {
          case Some(qual`.contains`()) => Some(qual)
          case _ => None
        }
      case qual`.contains`() => Some(qual)
      case _ => None
    }
  }
}

object `!.contains _` {
  def unapply(expr: ScExpression): Option[ScExpression] = {
    stripped(expr) match {
      case ScFunctionExpr(Seq(x), Some(result)) =>
        stripped(result) match {
          case !(qual`.contains`(stripped(ResolvesTo(`x`)))) if isIndependentOf(qual, x) => Some(qual)
          case _ => None
        }
      case _ => None
    }
  }
}

object FilterContainsToIntersect extends SimplificationType {
  override def hint: String = InspectionBundle.message("replace.filter.with.intersect")

  override def getSimplification(expr: ScExpression): Option[Simplification] = expr match {
    case qual`.filter`(other`.contains _`()) if isSet(qual) && isSet(other) =>
      Some(replace(expr).withText(invocationText(qual, "intersect", other)).highlightFrom(qual))
    case qual`.filterNot`(other`!.contains _`()) if isSet(qual) && isSet(other) =>
      Some(replace(expr).withText(invocationText(qual, "intersect", other)).highlightFrom(qual))
    case _ => None
  }
}

object FilterNotContainsToDiff extends SimplificationType {
  override def hint: String = InspectionBundle.message("replace.filter.with.diff")

  override def getSimplification(expr: ScExpression): Option[Simplification] = expr match {
    case qual`.filter`(other`!.contains _`()) if isSet(qual) && isSet(other) =>
      Some(replace(expr).withText(invocationText(qual, "diff", other)).highlightFrom(qual))
    case qual`.filterNot`(other`.contains _`()) if isSet(qual) && isSet(other) =>
      Some(replace(expr).withText(invocationText(qual, "diff", other)).highlightFrom(qual))
    case _ => None
  }
}
