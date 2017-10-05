package org.jetbrains.plugins.scala.codeInsight.template.macros

import com.intellij.codeInsight.template._
import org.jetbrains.plugins.scala.codeInsight.template.util.MacroUtil

/**
  * @author Roman.Shein
  *         Date: 21.12.2015
  */
class ScalaPrimaryConstructorParamNamesMacro extends Macro {
  override def calculateResult(params: Array[Expression], context: ExpressionContext): Result =
    Option(params.head.calculateResult(context).toString).map(MacroUtil.paramPairs(_).map(_._1)) match {
      case Some(head::tail) => new TextResult(tail.foldLeft(head)(_ + ", " + _))
      case _ => null
    }

  def getName: String = MacroUtil.scalaIdPrefix + "primaryConstructorParamNames"

  def getPresentableName: String = MacroUtil.scalaPresentablePrefix + "primaryConstructorParamNames"
}
