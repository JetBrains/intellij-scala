package org.jetbrains.plugins.scala.codeInsight.template

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.template.{Result, ExpressionContext, Expression, Macro}
import java.lang.String

/**
 * User: Alexander Podkhalyuzin
 * Date: 30.01.2009
 */

class ScalaVariableOfTypeMacro extends Macro {
  def calculateLookupItems(p1: Array[Expression], p2: ExpressionContext): Array[LookupElement] = Seq.empty.toArray

  def calculateResult(p1: Array[Expression], p2: ExpressionContext): Result = null

  def calculateQuickResult(p1: Array[Expression], p2: ExpressionContext): Result = null

  def getDescription: String = CodeInsightBundle.message("macro.variable.of.type")

  def getName: String = "scalaVariableOfType"

  def getDefaultValue: String = "x"

}