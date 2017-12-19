package org.jetbrains.plugins.scala.lang.psi.api.expr

import com.intellij.openapi.components.ServiceManager
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.types.ScType

/**
  * Nikolay.Tropin
  * 19-Dec-17
  */
trait ExpectedTypes {
  def smartExpectedType(expr: ScExpression, fromUnderscore: Boolean = true): Option[ScType]

  def expectedExprType(expr: ScExpression, fromUnderscore: Boolean = true): Option[(ScType, Option[ScTypeElement])]

  def expectedExprTypes(expr: ScExpression,
                        withResolvedFunction: Boolean = false,
                        fromUnderscore: Boolean = true): Array[(ScType, Option[ScTypeElement])]
}

object ExpectedTypes {
  def instance(): ExpectedTypes = ServiceManager.getService(classOf[ExpectedTypes])
}