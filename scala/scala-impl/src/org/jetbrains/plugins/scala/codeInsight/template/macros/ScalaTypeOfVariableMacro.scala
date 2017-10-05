package org.jetbrains.plugins.scala.codeInsight.template.macros

import com.intellij.codeInsight.template._
import org.jetbrains.plugins.scala.codeInsight.template.impl.ScalaCodeContextType
import org.jetbrains.plugins.scala.codeInsight.template.util.MacroUtil

/**
 * @author Roman.Shein
 * @since 29.09.2015.
 */
class ScalaTypeOfVariableMacro extends ScalaMacro {
  override def innerCalculateResult(params: Array[Expression], context: ExpressionContext): Result = {
    if (params.length == 0) return null
    Option(params(0).calculateResult(context)).flatMap(MacroUtil.resultToScExpr(_, context)).
            flatMap(_.getType().toOption).map(new ScalaTypeResult(_)).orNull
  }

  override def calculateQuickResult(params: Array[Expression], context: ExpressionContext): Result = calculateResult(params, context)

  override def isAcceptableInContext(context: TemplateContextType): Boolean = context.isInstanceOf[ScalaCodeContextType]

  override def getName: String = MacroUtil.scalaIdPrefix + "typeOfVariable"

  override def getPresentableName: String = MacroUtil.scalaPresentablePrefix + "typeOfVariable"

  override def getDefaultValue: String = "A"
}
