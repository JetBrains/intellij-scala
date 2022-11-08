package org.jetbrains.plugins.scala.codeInsight.template.macros

import com.intellij.codeInsight.template._
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightBundle

final class ScalaTypeOfVariableMacro extends ScalaMacro {

  override def getNameShort: String = "typeOfVariable"

  override def getDefaultValue: String = ScalaMacro.DefaultValue.toUpperCase

  override def calculateResult(params: Array[Expression], context: ExpressionContext): Result =
    params.headOption
      .map(_.calculateResult(context))
      .flatMap(resultToScExpr(_)(context))
      .map(ScalaTypeResult)
      .orNull

  override def calculateQuickResult(params: Array[Expression], context: ExpressionContext): Result =
    calculateResult(params, context)
}
