package org.jetbrains.plugins.scala.lang.psi.api.expr

import com.intellij.openapi.components.ServiceManager
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ExpectedTypes.ParameterType
import org.jetbrains.plugins.scala.lang.psi.types.ScType

/**
  * Nikolay.Tropin
  * 19-Dec-17
  */
trait ExpectedTypes {
  def smartExpectedType(expr: ScExpression, fromUnderscore: Boolean = true): Option[ScType]

  def expectedExprType(expr: ScExpression, fromUnderscore: Boolean = true): Option[ParameterType]

  def expectedExprTypes(expr: ScExpression,
                        withResolvedFunction: Boolean = false,
                        fromUnderscore: Boolean = true): Array[ParameterType]
}

object ExpectedTypes {
  type ParameterType = (ScType, Option[ScTypeElement])

  def instance(): ExpectedTypes = ServiceManager.getService(classOf[ExpectedTypes])
}