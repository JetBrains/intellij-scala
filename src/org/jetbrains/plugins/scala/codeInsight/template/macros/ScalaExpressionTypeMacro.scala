package org.jetbrains.plugins.scala.codeInsight.template.macros

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.template._
import org.jetbrains.plugins.scala.codeInsight.template.impl.ScalaCodeContextType
import org.jetbrains.plugins.scala.codeInsight.template.util.MacroUtil
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem

/**
 * @author Roman.Shein
 * @since 22.09.2015.
 */
class ScalaExpressionTypeMacro extends ScalaMacro {

  override def innerCalculateResult(params: Array[Expression], context: ExpressionContext)
                                   (implicit typeSystem: TypeSystem): Result = {
    if (params.length != 1) return null
    MacroUtil.resultToScExpr(params.head.calculateResult(context), context).flatMap(_.getType().toOption).
            map(myType => new ScalaTypeResult(myType)).orNull
  }

  override def getName: String = MacroUtil.scalaIdPrefix + "expressionType"

  override def getPresentableName: String = MacroUtil.scalaPresentablePrefix + CodeInsightBundle.message("macro.expression.type")

  override def isAcceptableInContext(context: TemplateContextType): Boolean = context.isInstanceOf[ScalaCodeContextType]
}
