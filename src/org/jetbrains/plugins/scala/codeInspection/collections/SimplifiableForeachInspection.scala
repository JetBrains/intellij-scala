package org.jetbrains.plugins.scala
package codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.extensions.ResolvesTo
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScFunctionExpr, ScMethodCall}

/**
  * @author Victor Malov
  * 2016-08-16
  */
class SimplifiableForeachInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] = Array(SimplifiableForeach)
}

object SimplifiableForeach extends SimplificationType {
  override def hint: String = InspectionBundle.message("convertible.to.method.value.name")

  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    expr match {
      case qual`.foreach`(methodCall(name)) =>
        Some(replace(expr).withText(invocationText(qual, "foreach", name)))
      case _ => None
    }
  }

  private object methodCall {
    def unapply(expr: ScExpression) = stripped(expr) match {
      case ScFunctionExpr(Seq(s), Some(ScMethodCall(name, Seq(ResolvesTo(param))))) if s == param => {
        Some(name)
      }
      case _ => None
    }
  }
}