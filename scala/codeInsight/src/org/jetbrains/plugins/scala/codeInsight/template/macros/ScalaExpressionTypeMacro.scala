package org.jetbrains.plugins.scala
package codeInsight
package template
package macros

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.template._

final class ScalaExpressionTypeMacro extends ScalaMacro {

  override def calculateResult(params: Array[Expression], context: ExpressionContext): Result = params match {
    case Array(param) =>
      val maybeType = resultToScExpr(param.calculateResult(context))(context)
      maybeType.map(ScalaTypeResult).orNull
    case _            => null
  }

  override def getPresentableName: String = CodeInsightBundle.message("macro.expression.type")
}
