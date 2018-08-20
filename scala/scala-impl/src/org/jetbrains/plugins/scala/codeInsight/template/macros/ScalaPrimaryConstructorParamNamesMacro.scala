package org.jetbrains.plugins.scala
package codeInsight
package template
package macros

import com.intellij.codeInsight.template._
import org.jetbrains.plugins.scala.codeInsight.template.util.MacroUtil
import org.jetbrains.plugins.scala.extensions._

/**
  * @author Roman.Shein
  *         Date: 21.12.2015
  */
class ScalaPrimaryConstructorParamNamesMacro extends ScalaMacro("macro.primaryConstructor.param.names") {

  override def calculateResult(params: Array[Expression], context: ExpressionContext): Result =
    params.headOption.flatMap(_.calculateResult(context).toOption)
      .map { result =>
        val list = MacroUtil.paramPairs(result.toString)
        val text = list.map(_._1).commaSeparated()
        new TextResult(text)
      }.orNull

  override protected def message(nameKey: String): String = ScalaMacro.message(nameKey)
}
