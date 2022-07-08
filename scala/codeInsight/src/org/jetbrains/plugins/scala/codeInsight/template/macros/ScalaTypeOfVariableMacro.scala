package org.jetbrains.plugins.scala
package codeInsight
package template
package macros

import com.intellij.codeInsight.template._

final class ScalaTypeOfVariableMacro extends ScalaMacro {

  override def calculateResult(params: Array[Expression], context: ExpressionContext): Result =
    params.headOption
      .map(_.calculateResult(context))
      .flatMap(resultToScExpr(_)(context))
      .map(ScalaTypeResult)
      .orNull

  override def calculateQuickResult(params: Array[Expression], context: ExpressionContext): Result =
    calculateResult(params, context)

  override def getDefaultValue: String = ScalaMacro.DefaultValue.toUpperCase

  override def getPresentableName: String = ScalaCodeInsightBundle.message("macro.type.of.variable")
}
