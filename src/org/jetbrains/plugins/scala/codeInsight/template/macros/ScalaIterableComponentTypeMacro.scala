package org.jetbrains.plugins.scala.codeInsight.template.macros

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.template._
import org.jetbrains.plugins.scala.codeInsight.template.impl.ScalaCodeContextType
import org.jetbrains.plugins.scala.codeInsight.template.util.MacroUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem

/**
 * @author Roman.Shein
 * @since 25.09.2015.
 */
class ScalaIterableComponentTypeMacro extends ScalaMacro {
  override def innerCalculateResult(params: Array[Expression], context: ExpressionContext)
                                   (implicit typeSystem: TypeSystem): Result = {
    if (params.length != 1) return null
    Option(params(0).calculateResult(context)).flatMap(MacroUtil.resultToScExpr(_, context)).flatMap(_.getType().
            toOption.flatMap{ exprType =>
              MacroUtil.getComponentFromArrayType(exprType) match {
                case Some(arrComponentType) => Some(arrComponentType)
                case None =>
                  ScType.extractClass(exprType, Some(context.getProject)) match {
                    case Some(x: ScTypeDefinition) if x.functionsByName("foreach").nonEmpty => Some(exprType)
                    case _ => None
                  }
              }
            }
    ).map(new ScalaTypeResult(_)).orNull
  }

  override def getName: String = MacroUtil.scalaIdPrefix + "iterableComponentType"

  override def getPresentableName: String = MacroUtil.scalaPresentablePrefix + CodeInsightBundle.message("macro.iterable.component.type")

  override def calculateQuickResult(params: Array[Expression], context: ExpressionContext): Result = calculateResult(params, context)

  override def getDefaultValue = "a"

  override def isAcceptableInContext(context: TemplateContextType): Boolean = context.isInstanceOf[ScalaCodeContextType]
}
