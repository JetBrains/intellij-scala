package org.jetbrains.plugins.scala
package codeInsight
package template
package macros

import com.intellij.codeInsight.template._

/**
 * @author Roman.Shein
 * @since 22.09.2015.
 */
class ScalaExpressionTypeMacro extends ScalaMacro("macro.expression.type") {

  override def calculateResult(params: Array[Expression], context: ExpressionContext): Result = params match {
    case Array(head) =>
      resultToScExpr(head.calculateResult(context))(context)
        .map(myType => new ScalaTypeResult(myType))
        .orNull
    case _ => null
  }
}
