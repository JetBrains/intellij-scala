package org.jetbrains.plugins.scala.codeInspection.collections

import org.jetbrains.plugins.scala.codeInspection.InspectionBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScMethodCall}

/**
  * @author mattfowler
  */
class IfElseToFilteredOptionInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] = Array(FilterOption)
}

object FilterOption extends SimplificationType {
  override def hint: String = InspectionBundle.message("ifstmt.to.filteredOption")

  override def getSimplification(expr: ScExpression): Option[Simplification] = expr match {
    case ex@IfStmt(ScMethodCall(method, Seq(methodArg)), some@scalaSome(_), scalaNone()) =>
      replaceIfEqual(ex, method, methodArg, some)
    case ex@IfStmt(ScMethodCall(method, Seq(methodArg)), option@scalaOption(_), scalaNone()) =>
      replaceIfEqual(ex, method, methodArg, option)
    case _ => None
  }

  private def replaceIfEqual(expression: ScExpression,
                             methodCall: ScExpression,
                             methodArgument: ScExpression,
                             option: ScExpression): Option[Simplification] = {
    val replaceWith = getReplacement(_: String, expression, methodArgument, methodCall)
    option match {
      case scalaSome(arg) if argsEqual(methodArgument, arg) => Some(replaceWith("Some"))
      case scalaOption(arg) if argsEqual(methodArgument, arg) => Some(replaceWith("Option"))
      case _ => None
    }
  }

  private def argsEqual(firstArg: ScExpression, secondArg: ScExpression) = firstArg.getText == secondArg.getText

  private def getReplacement(optionCall: String, expression: ScExpression, methodArg: ScExpression, methodCall: ScExpression) = {
    replace(expression).withText(s"$optionCall(${methodArg.getText}).filter(${methodCall.getText})")
  }
}
