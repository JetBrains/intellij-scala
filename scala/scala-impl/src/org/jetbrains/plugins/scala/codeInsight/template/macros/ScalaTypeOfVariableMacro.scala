package org.jetbrains.plugins.scala
package codeInsight
package template
package macros

import com.intellij.codeInsight.template._
import org.jetbrains.plugins.scala.codeInsight.template.util.MacroUtil

/**
 * @author Roman.Shein
 * @since 29.09.2015.
 */
class ScalaTypeOfVariableMacro extends ScalaMacro("macro.variable.of.type") {

  override def calculateResult(params: Array[Expression], context: ExpressionContext): Result = {
    if (params.length == 0) return null
    Option(params(0).calculateResult(context)).flatMap(MacroUtil.resultToScExpr(_, context)).
      flatMap(_.`type`().toOption).map(new ScalaTypeResult(_)).orNull
  }

  override def calculateQuickResult(params: Array[Expression], context: ExpressionContext): Result = calculateResult(params, context)

  override def getDefaultValue: String = ScalaMacro.DefaultValue.toUpperCase

  override protected def message(nameKey: String): String = ScalaMacro.message(nameKey)
}
