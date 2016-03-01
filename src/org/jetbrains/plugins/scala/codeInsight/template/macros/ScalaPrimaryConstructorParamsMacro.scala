package org.jetbrains.plugins.scala.codeInsight.template.macros

import com.intellij.codeInsight.template._
import org.jetbrains.plugins.scala.codeInsight.template.util.MacroUtil

/**
  * @author Roman.Shein
  *         Date: 21.12.2015
  */
class ScalaPrimaryConstructorParamsMacro extends Macro {
  override def calculateResult(params: Array[Expression], context: ExpressionContext): Result = {
    MacroUtil.getPrimaryConbstructorParams(context).map(new PsiElementResult(_)).orNull
  }

  def getName: String = MacroUtil.scalaIdPrefix + "primaryConstructorParams"

  def getPresentableName: String = MacroUtil.scalaPresentablePrefix + "primaryConstructorParams"
}
