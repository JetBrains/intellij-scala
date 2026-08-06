package org.jetbrains.plugins.scala.codeInsight.template.macros

import com.intellij.codeInsight.template._
import com.intellij.java.JavaBundle

final class ScalaExpressionTypeMacro extends ScalaMacro {

  override def calculateResult(params: Array[Expression], context: ExpressionContext): Result = params match {
    case Array(param) =>
      val maybeType = resultToScExpr(param.calculateResult(context))(context)
      maybeType.map(ScalaTypeResult).orNull
    case _            => null
  }

  override def getNameShort: String = "expressionType"

  override def getPresentableName: String = JavaBundle.message("macro.expression.type")
}
