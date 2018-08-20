package org.jetbrains.plugins.scala
package codeInsight
package template
package macros

import com.intellij.codeInsight.template._

/**
 * @author Roman.Shein
 * @since 29.09.2015.
 */
class ScalaTypeOfVariableMacro extends ScalaMacro("macro.variable.of.type") {

  override def calculateResult(params: Array[Expression], context: ExpressionContext): Result = params.headOption.map {
    _.calculateResult(context)
  }.flatMap {
    resultToScExpr(_)(context)
  }.map(new ScalaTypeResult(_))
    .orNull

  override def calculateQuickResult(params: Array[Expression], context: ExpressionContext): Result = calculateResult(params, context)

  override def getDefaultValue: String = ScalaMacro.DefaultValue.toUpperCase

  override protected def message(nameKey: String): String = ScalaMacro.message(nameKey)
}
