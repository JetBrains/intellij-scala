package org.jetbrains.plugins.scala.lang.psi.api.expr

import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ExpectedTypes.ParameterType
import org.jetbrains.plugins.scala.lang.psi.types.ScType

trait ExpectedTypes {
  def smartExpectedType(expr: ScExpression, fromUnderscore: Boolean = true): Option[ScType]

  def expectedExprType(expr: ScExpression, fromUnderscore: Boolean = true): Option[ParameterType]

  def expectedExprTypes(expr: ScExpression,
                        withResolvedFunction: Boolean = false,
                        fromUnderscore: Boolean = true): Array[ParameterType]

  /**
   * The expected type of `expr`, given the expected types of it for several alternatives,
   * e.g. the parameter types of the overloaded alternatives of the method it is an argument of.
   */
  def expectedTypeFromAlternatives(expr: ScExpression, alternatives: Seq[ScType]): Option[ScType]
}

object ExpectedTypes {
  type ParameterType = (ScType, Option[ScTypeElement])

  def instance(): ExpectedTypes = ApplicationManager.getApplication.getService(classOf[ExpectedTypes])
}