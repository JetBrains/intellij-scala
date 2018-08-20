package org.jetbrains.plugins.scala
package codeInsight
package template
package macros

import com.intellij.codeInsight.template._

/**
 * @author Roman.Shein
 * @since 25.09.2015.
 */
class ScalaIterableComponentTypeMacro extends ScalaMacro("macro.iterable.component.type") {

  override def calculateResult(params: Array[Expression], context: ExpressionContext): Result = params match {
    case Array(head) =>
      Option(head.calculateResult(context))
        .flatMap(resultToScExpr(_)(context))
        .flatMap { exprType =>
          arrayComponent(exprType).orElse {
            Some(exprType).filter(ScalaVariableOfTypeMacroBase.isIterable)
          }
        }.map(new ScalaTypeResult(_)).orNull
    case _ => null
  }

  override def calculateQuickResult(params: Array[Expression], context: ExpressionContext): Result = calculateResult(params, context)

  override def getDefaultValue: String = ScalaMacro.DefaultValue
}
