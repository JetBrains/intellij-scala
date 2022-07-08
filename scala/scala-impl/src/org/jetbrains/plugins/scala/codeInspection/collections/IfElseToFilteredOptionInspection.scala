package org.jetbrains.plugins.scala
package codeInspection
package collections

import com.intellij.codeInsight.PsiEquivalenceUtil._
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScMethodCall}
import org.jetbrains.plugins.scala.util.SideEffectsUtil

import scala.collection.immutable.ArraySeq

class IfElseToFilteredOptionInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: ArraySeq[SimplificationType] = ArraySeq(FilterOption)
}

object FilterOption extends SimplificationType {
  override def hint: String = ScalaInspectionBundle.message("ifstmt.to.filteredOption")

  override def getSimplification(expr: ScExpression): Option[Simplification] = expr match {
    case ex@IfStmt(ScMethodCall(method, Seq(methodArg)), some@scalaSome(_), scalaNone()) =>
      replaceIfEquivalent(ex, method, methodArg, some)
    case ex@IfStmt(ScMethodCall(method, Seq(methodArg)), option@scalaOption(_), scalaNone()) =>
      replaceIfEquivalent(ex, method, methodArg, option)
    case _ => None
  }

  private def replaceIfEquivalent(ex: ScExpression,
                                     method: ScExpression,
                                     methodArg: ScExpression,
                                     option: ScExpression) = {
    if (SideEffectsUtil.hasNoSideEffects(methodArg))
      replaceIfEqual(ex, method, methodArg, option)
    else
      None
  }

  private def replaceIfEqual(expression: ScExpression,
                             methodCall: ScExpression,
                             methodArgument: ScExpression,
                             option: ScExpression): Option[Simplification] = {
    val replaceWith = getReplacement(_: String, expression, methodArgument, methodCall)
    option match {
      case scalaSome(arg) if areElementsEquivalent(methodArgument, arg) => Some(replaceWith("Some"))
      case scalaOption(arg) if areElementsEquivalent(methodArgument, arg) => Some(replaceWith("Option"))
      case _ => None
    }
  }

  private def getReplacement(optionCall: String, expression: ScExpression, methodArg: ScExpression, methodCall: ScExpression) = {
    replace(expression).withText(s"$optionCall(${methodArg.getText}).filter(${methodCall.getText})")
  }
}
