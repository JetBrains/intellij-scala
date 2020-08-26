package org.jetbrains.plugins
package scala
package codeInspection
package internal

import org.jetbrains.plugins.scala.codeInspection.collections.{OperationOnCollectionInspection, Qualified, Simplification, SimplificationType, invocation, invocationText}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

class ScalaShouldBeTextContainsInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] =
    Array(ScalaShouldBeTextContainsInspection)
}

object ScalaShouldBeTextContainsInspection extends SimplificationType() {
  override val hint: String = ScalaInspectionBundle.message("internal.replace.with.textContains")

  private val `.getText`: Qualified = invocation("getText").from(Array(psiElementFqn, psiASTNodeFqn))
  private val `.contains`: Qualified = invocation("contains")

  override def getSimplification(expr: ScExpression): Option[Simplification] = expr match {
    // TODO infix notation?
    case `.getText`(base)`.contains`(charExpr(arg)) =>
      Some(replace(expr).withText(invocationText(base, "textContains", arg)).highlightFrom(base))
    case _ =>
      None
  }
}
