package org.jetbrains.plugins.scala
package codeInspection
package collections

import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScFunctionExpr, ScMethodCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition

import scala.collection.immutable.ArraySeq

class EmptyCheckInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: ArraySeq[SimplificationType] =
    ArraySeq(CheckIsEmpty, CheckNonEmpty, CheckIsDefined)
}

object CheckIsEmpty extends SimplificationType {
  override def hint: String = ScalaInspectionBundle.message("replace.with.isEmpty")

  override def getSimplification(expr: ScExpression): Option[Simplification] = expr match {
    // TODO infix notation?
    case `.isEmpty`(_) => None
    case CheckIsEmpty(qual, start, end) if !isArray(qual) =>
      Some(replace(expr).withText(invocationText(qual, "isEmpty")).highlightRange(start, end))
    case _ => None
  }

  def unapply(expr: ScExpression): Option[(ScExpression, Int, Int)] = {
    val firstLevel = expr match {
      // TODO infix notation?
      case `.sizeOrLength`(coll) `==` literal("0") => Some((coll, coll.end, expr.end))
      case `.isEmpty`(coll) => Some((coll, coll.end, expr.end))
      case `!`(CheckNonEmpty(coll, _, _)) => Some((coll, expr.start, expr.end))
      case `!`(CheckIsDefined(coll, _, _))=> Some((coll, expr.start, expr.end))
      case coll `==` scalaNone() if isOption(coll) => Some((coll, coll.end, expr.end))
      case scalaNone() `==` coll if isOption(coll) => Some((coll, expr.start, coll.start))
      case _ => None
    }
    extractInner(firstLevel)
  }

  def extractInner(firstLevel: Option[(ScExpression, Int, Int)]): Option[(ScExpression, Int, Int)] = {
    // TODO infix notation?
    firstLevel match {
      case None => None
      case Some((inner @ `.headOption`(coll), start, end)) =>
        Some(coll, Math.min(coll.end, start), Math.max(inner.end, end))
      case Some((inner @ `.lastOption`(coll), start, end)) =>
        Some(coll, Math.min(coll.end, start), Math.max(inner.end, end))
      case _ => firstLevel
    }
  }
}

object CheckNonEmpty extends SimplificationType {
  override def hint: String = ScalaInspectionBundle.message("replace.with.nonEmpty")

  override def getSimplification(expr: ScExpression): Option[Simplification] = expr match {
    // TODO infix notation?
    case `.nonEmpty`(_) => None
    case CheckNonEmpty(qual, start, end) if !isOption(qual) && !isArray(qual) =>
      Some(replace(expr).withText(invocationText(qual, "nonEmpty")).highlightRange(start, end))
    case _ => None
  }

  def unapply(expr: ScExpression): Option[(ScExpression, Int, Int)] = {
    // TODO infix notation?
    val firstLevel = expr match {
      case `.nonEmpty`(qual) => Some((qual, qual.end, expr.end))
      case `.sizeOrLength`(qual) `!=` literal("0") => Some((qual, qual.end, expr.end))
      case `.sizeOrLength`(qual) `>` literal("0") => Some((qual, qual.end, expr.end))
      case `.sizeOrLength`(qual) `>=` literal("1") => Some((qual, qual.end, expr.end))
      case qual`.exists`(ScFunctionExpr(_, Some(literal("true")))) => Some((qual, qual.end, expr.end))
      case qual`.exists`(ScMethodCall(f: ScReferenceExpression, Seq(literal("true")))) if isConstFunction(f) =>
        Some((qual, qual.end, expr.end))
      case `!`(CheckIsEmpty(qual, _, _)) => Some(qual, expr.start, expr.end)
      case qual `!=` scalaNone() if isOption(qual) => Some(qual, qual.end, expr.end)
      case scalaNone() `!=` qual if isOption(qual) => Some(qual, expr.start, qual.start)
      case `.isDefined`(qual) if isOption(qual) => Some(qual, qual.end, expr.end)
      case _ => None
    }
    CheckIsEmpty.extractInner(firstLevel)
  }

  private def isConstFunction(f: ScReferenceExpression) = f.getText.endsWith("const") && (f.resolve() match {
    case d: ScFunctionDefinition => d.containingClass.qualifiedName == "scala.Function"
    case _ => false
  })

}

object CheckIsDefined extends SimplificationType {
  override def hint: String = ScalaInspectionBundle.message("replace.with.isDefined")

  override def getSimplification(expr: ScExpression): Option[Simplification] = expr match {
    // TODO infix notation?
    case `.isDefined`(_) => None
    case CheckIsDefined(qual, start, end) =>
      Some(replace(expr).withText(invocationText(qual, "isDefined")).highlightRange(start, end))
    case _ => None
  }

  def unapply(expr: ScExpression): Option[(ScExpression, Int, Int)] = {
    expr match {
      case CheckNonEmpty(qual, start, end) if isOption(qual) => Some((qual, start, end))
      case _ => None
    }
  }
}
