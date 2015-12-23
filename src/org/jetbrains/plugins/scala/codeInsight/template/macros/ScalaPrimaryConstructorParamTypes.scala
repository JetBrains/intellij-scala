package org.jetbrains.plugins.scala.codeInsight.template.macros

import com.intellij.codeInsight.template._
import org.jetbrains.plugins.scala.codeInsight.template.util.MacroUtil

/**
  * @author Roman.Shein
  *         Date: 21.12.2015
  */
class ScalaPrimaryConstructorParamTypes extends Macro {
  override def calculateResult(params: Array[Expression], context: ExpressionContext): Result = {
    MacroUtil.getPrimaryConbstructorParams(context).map(_.map(_.getRealParameterType().getOrAny)).
      map { params =>
        if (params.isEmpty) ""
        else {
          val (pre, post) = if (params.size > 1) ("(", ")") else ("", "")
          pre + params.tail.foldLeft(params.head.toString)(_ + ", " + _) + post
        }
      }.map(new TextResult(_)).orNull
  }

  def getName: String = MacroUtil.scalaIdPrefix + "primaryConstructorParamTypes"

  def getPresentableName: String = MacroUtil.scalaPresentablePrefix + "primaryConstructorParamTypes"
}
