package org.jetbrains.plugins.scala
package codeInsight
package template
package macros

import com.intellij.codeInsight.template._
import org.jetbrains.plugins.scala.codeInsight.template.util.MacroUtil

/**
 * @author Roman.Shein
 * @since 22.09.2015.
 */
class ScalaExpressionTypeMacro extends ScalaMacro("macro.expression.type") {

  override def calculateResult(params: Array[Expression], context: ExpressionContext): Result = {
    if (params.length != 1) return null
    MacroUtil.resultToScExpr(params.head.calculateResult(context), context).flatMap(_.`type`().toOption).
            map(myType => new ScalaTypeResult(myType)).orNull
  }
}
