package org.jetbrains.plugins.scala
package codeInspection
package collections

import org.jetbrains.plugins.scala.extensions.ResolvesTo
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScFunctionExpr, ScUnderscoreSection}

import scala.collection.immutable.ArraySeq

class FilterOtherContainsInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: ArraySeq[SimplificationType] =
    ArraySeq(FilterContainsToIntersect, FilterNotContainsToDiff)
}

object `.contains _` {
  def unapply(expr: ScExpression): Option[ScExpression] = {
    stripped(expr) match {
      case ScFunctionExpr(Seq(x), Some(result)) =>
        stripped(result) match {
          case qual`.contains`(stripped(ResolvesTo(`x`))) if isIndependentOf(qual, x) => Some(qual)
          case _ => None
        }
      case qual`.contains` underscore() => Some(qual)
      case undSect: ScUnderscoreSection =>
        undSect.bindingExpr match {
          // TODO infix notation?
          case Some(`.contains`(qual)) => Some(qual)
          case _ => None
        }
      case `.contains`(qual) => Some(qual)
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
  override def hint: String = ScalaInspectionBundle.message("replace.filter.with.intersect")

  override def getSimplification(expr: ScExpression): Option[Simplification] = expr match {
    case qual`.filter` `.contains _`(other) if isSet(qual) && isSet(other) =>
      Some(replace(expr).withText(invocationText(qual, "intersect", other)).highlightFrom(qual))
    case qual`.filterNot` `!.contains _`(other) if isSet(qual) && isSet(other) =>
      Some(replace(expr).withText(invocationText(qual, "intersect", other)).highlightFrom(qual))
    case _ => None
  }
}

object FilterNotContainsToDiff extends SimplificationType {
  override def hint: String = ScalaInspectionBundle.message("replace.filter.with.diff")

  override def getSimplification(expr: ScExpression): Option[Simplification] = expr match {
    case qual`.filter` `!.contains _`(other) if isSet(qual) && isSet(other) =>
      Some(replace(expr).withText(invocationText(qual, "diff", other)).highlightFrom(qual))
    case qual`.filterNot` `.contains _`(other) if isSet(qual) && isSet(other) =>
      Some(replace(expr).withText(invocationText(qual, "diff", other)).highlightFrom(qual))
    case _ => None
  }
}
