package org.jetbrains.plugins.scala.codeInsight.template.macros

import com.intellij.codeInsight.template._
import org.jetbrains.plugins.scala.codeInsight.template.util.MacroUtil
import org.jetbrains.plugins.scala.extensions.ObjectExt

/**
  * @author Roman.Shein
  *         Date: 21.12.2015
  */
class ScalaPrimaryConstructorParamTypesMacro extends Macro {
  override def calculateResult(params: Array[Expression], context: ExpressionContext): Result = {
    val result = params.headOption.flatMap(_.calculateResult(context).toOption).map(_.toString)
    result.map(MacroUtil.paramPairs(_).map(_._2)) match {
      case Some(head::tail) => new TextResult(addParens(tail.foldLeft(head)(_ + ", " + _), tail.nonEmpty))
      case _ => null
    }
  }

  def getName: String = MacroUtil.scalaIdPrefix + "primaryConstructorParamTypes"

  def getPresentableName: String = MacroUtil.scalaPresentablePrefix + "primaryConstructorParamTypes"

  private def addParens(text: String, doAdd: Boolean) = if (doAdd) "(" + text + ")" else text
}
