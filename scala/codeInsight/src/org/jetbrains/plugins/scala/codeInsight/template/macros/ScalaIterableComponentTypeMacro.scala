package org.jetbrains.plugins.scala.codeInsight.template.macros

import com.intellij.codeInsight.template._
import com.intellij.java.JavaBundle

final class ScalaIterableComponentTypeMacro extends ScalaMacro {

  override def calculateResult(params: Array[Expression], context: ExpressionContext): Result = params match {
    case Array(head) =>
      Option(head.calculateResult(context))
        .flatMap(resultToScExpr(_)(context))
        .flatMap { exprType =>
          arrayComponent(exprType).orElse {
            Some(exprType).filter(ScalaVariableOfTypeMacro.isIterable)
          }
        }.map(ScalaTypeResult).orNull
    case _ => null
  }

  override def calculateQuickResult(params: Array[Expression], context: ExpressionContext): Result = calculateResult(params, context)

  override def getDefaultValue: String = ScalaMacro.DefaultValue

  override def getPresentableName: String = JavaBundle.message("macro.iterable.component.type")
}
