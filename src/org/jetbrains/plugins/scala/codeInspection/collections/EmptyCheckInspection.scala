package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

/**
 * @author Nikolay.Tropin
 */
class EmptyCheckInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] =
    Array(CheckIsEmpty, CheckNonEmpty, CheckIsDefined)
}

object CheckIsEmpty extends SimplificationType {
  override def hint: String = InspectionBundle.message("replace.with.isEmpty")

  override def getSimplification(expr: ScExpression): Option[Simplification] = expr match {
    case _`.isEmpty`() => None
    case CheckIsEmpty(qual) if !isArray(qual) =>
      Some(replace(expr).withText(invocationText(qual, "isEmpty")).highlightFrom(qual))
    case _ => None
  }

  def unapply(expr: ScExpression): Option[ScExpression] = {
    expr match {
      case (coll`.sizeOrLength`()) `==` literal("0") => Some(coll)
      case coll`.isEmpty`() => Some(coll)
      case `!`(CheckNonEmpty(coll)) => Some(coll)
      case `!`(CheckIsDefined(coll)) => Some(coll)
      case coll `==` scalaNone() if isOption(coll) => Some(coll)
      case _ => None
    }
  }
}

object CheckNonEmpty extends SimplificationType {
  override def hint: String = InspectionBundle.message("replace.with.nonEmpty")

  override def getSimplification(expr: ScExpression): Option[Simplification] = expr match {
    case qual`.nonEmpty`() => None
    case CheckNonEmpty(qual) if !isOption(qual) && !isArray(qual) =>
      Some(replace(expr).withText(invocationText(qual, "nonEmpty")).highlightFrom(qual))
    case _ => None
  }

  def unapply(expr: ScExpression): Option[ScExpression] = {
    expr match {
      case qual`.nonEmpty`() => Some(qual)
      case (qual`.sizeOrLength`()) `!=` literal("0") => Some(qual)
      case (qual`.sizeOrLength`()) `>` literal("0") => Some(qual)
      case (qual`.sizeOrLength`()) `>=` literal("1") => Some(qual)
      case `!`(CheckIsEmpty(qual)) => Some(qual)
      case qual `!=` scalaNone() if isOption(qual) => Some(qual)
      case qual`.isDefined`() if isOption(qual) => Some(qual)
      case _ => None
    }
  }
}

object CheckIsDefined extends SimplificationType {
  override def hint: String = InspectionBundle.message("replace.with.isDefined")

  override def getSimplification(expr: ScExpression): Option[Simplification] = expr match {
    case _`.isDefined`() => None
    case CheckIsDefined(qual) =>
      Some(replace(expr).withText(invocationText(qual, "isDefined")).highlightFrom(qual))
    case _ => None
  }

  def unapply(expr: ScExpression): Option[ScExpression] = {
    expr match {
      case CheckNonEmpty(qual) if isOption(qual) => Some(qual)
      case _ => None
    }
  }
}