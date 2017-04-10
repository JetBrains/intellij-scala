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
    case ex@IfStmt(ScMethodCall(method, Seq(methodArg)), scalaSome(someArg), scalaNone()) =>
      replaceIfEqual(ex, method, methodArg, someArg)
    case ex@IfStmt(ScMethodCall(method, Seq(methodArg)), scalaOption(optionArg), scalaNone()) =>
      replaceIfEqual(ex, method, methodArg, optionArg)
    case _ => None
  }

  private def replaceIfEqual(expression: ScExpression,
                             methodCall: ScExpression,
                             methodArgument: ScExpression,
                             optionArgument: ScExpression): Option[Simplification] = {
    if (methodArgument.getText == optionArgument.getText)
      Some(replace(expression).withText(s"Option(${methodArgument.getText}).filter(${methodCall.getText})"))
    else
      None
  }
}
