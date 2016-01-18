package org.jetbrains.plugins.scala.codeInsight.template.macros

import com.intellij.codeInsight.template._
import org.jetbrains.plugins.scala.codeInsight.template.util.MacroUtil

/**
  * @author Roman.Shein
  *         Date: 21.12.2015
  */
class ScalaPrimaryConstructorParamNames extends Macro {
  override def calculateResult(params: Array[Expression], context: ExpressionContext): Result =
    MacroUtil.getPrimaryConbstructorParams(context).
      map(_.map(param => param.getName)).
      map { params => if (params.isEmpty) "" else params.tail.foldLeft(params.head)(_ + ", " + _) }.
      map(new TextResult(_)).orNull

  def getName: String = MacroUtil.scalaIdPrefix + "primaryConstructorParamNames"

  def getPresentableName: String = MacroUtil.scalaPresentablePrefix + "primaryConstructorParamNames"
}
