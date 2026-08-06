package org.jetbrains.plugins.scala.codeInspection.internal

import org.jetbrains.plugins.scala.codeInspection.collections.{OperationOnCollectionInspection, Qualified, Simplification, SimplificationType, `!=`, `==`, invocation, invocationText}
import org.jetbrains.plugins.scala.codeInspection.{ExpressionOfTypeMatcher, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

import scala.collection.immutable.ArraySeq

class ScalaShouldBeTextMatchesInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: ArraySeq[SimplificationType] =
    ArraySeq(ScalaShouldBeTextMatchesInspection)
}

object ScalaShouldBeTextMatchesInspection extends SimplificationType() {
  override val hint: String = ScalaInspectionBundle.message("internal.replace.with.textMatches")

  private val `.getText`: Qualified = invocation("getText").from(ArraySeq(psiElementFqn))

  private val stringExpr = new ExpressionOfTypeMatcher("java.lang.String")

  override def getSimplification(expr: ScExpression): Option[Simplification] = expr match {
    // TODO infix notation?
    case `.getText`(base) `==` (stringExpr(str)) =>
      Some(replace(expr).withText(invocationText(base, "textMatches", str)).highlightFrom(base))
    case stringExpr(str) `==` (`.getText`(base)) =>
      Some(replace(expr).withText(invocationText(base, "textMatches", str)).highlightAll)
    case `.getText`(base) `!=` (stringExpr(str)) =>
      Some(replace(expr).withText(invocationText(negation = true, base, "textMatches", str)).highlightFrom(base))
    case stringExpr(str) `!=` (`.getText`(base)) =>
      Some(replace(expr).withText(invocationText(negation = true, base, "textMatches", str)).highlightAll)
    case _ =>
      None
  }
}
