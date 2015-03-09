package org.jetbrains.plugins.scala
package codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScMethodCall, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

/**
 * Nikolay.Tropin
 * 2014-05-06
 */
class ExistsEqualsInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] =
    Array(ExistsEquals)
}

object ExistsEquals extends SimplificationType {
  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    expr match {
      case qual`.exists`(equalsWith(e)) if canBeReplacedWithContains(qual, e) =>
        Some(replace(expr).withText(invocationText(qual, "contains", Seq(e))))
      case _ => None
    }
  }

  override def hint = InspectionBundle.message("exists.equals.hint")

  private def canBeReplacedWithContains(qual: ScExpression, arg: ScExpression) = {
    val exprText = s"(${qual.getText}).contains(${arg.getText})"
    ScalaPsiElementFactory.createExpressionWithContextFromText(exprText, qual.getContext, qual) match {
      case ScMethodCall(ref: ScReferenceExpression, Seq(a)) =>
        ref.resolve() != null &&
        a.expectedType(fromUnderscore = false).exists(a.getType().getOrNothing.conforms(_))
      case _ => false
    }

  }
}
