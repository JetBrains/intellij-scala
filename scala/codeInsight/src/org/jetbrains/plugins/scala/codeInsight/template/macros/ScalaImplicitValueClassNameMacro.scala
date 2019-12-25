package org.jetbrains.plugins.scala.codeInsight.template.macros

import com.intellij.codeInsight.template.{Expression, ExpressionContext, Result, TextResult}
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightBundle
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScParameterizedTypeElement

final class ScalaImplicitValueClassNameMacro extends ScalaMacro {

  override def getPresentableName: String = ScalaCodeInsightBundle.message("macro.implicit.value.class.name")

  // TODO 1: parametrize from settings
  private val Suffix = "Ops"

  override def calculateResult(params: Array[Expression], context: ExpressionContext): Result = {
    val targetTypeName = params match {
      case Array(p) if p != null => p
      case _ => return null
    }
    val name = calculateName(targetTypeName)(context)
    name.map(new TextResult(_)).orNull
  }

  override def calculateQuickResult(params: Array[Expression], context: ExpressionContext): Result = {
    val targetTypeName = params match {
      case Array(p) if p != null => p
      case _ => return null
    }
    val name = calculateQuickName(targetTypeName)(context)
    new TextResult(name)
  }

  private def calculateName(targetTypeName: Expression)(implicit context: ExpressionContext) = {
    val typeElement = scTypeElement(targetTypeName)(context)
    typeElement.map {
      case generic: ScParameterizedTypeElement =>
        val typeParams     = collectGenericParamNames(generic)
        val typeParamsText = if (typeParams.isEmpty) "" else s"[${typeParams.mkString(", ")}]"
        generic.typeElement.getText + Suffix + typeParamsText
      case typ                                 =>
        typ.getText + Suffix
    }
  }

  private def collectGenericParamNames(generic: ScParameterizedTypeElement): Seq[String] = {
    val withResolvedTypes = generic.typeArgList.typeArgs.map(ta => (ta, ta.`type`()))
    val genericTypeParams = withResolvedTypes.filter(_._2.isLeft).map(_._1)
    genericTypeParams.map(_.getText)
  }

  private def calculateQuickName(param: Expression)(implicit context: ExpressionContext): String = {
    def afterLatDot(s: String) = s.lastIndexOf('.') match {
      case -1  => s
      case idx => s.substring(idx)
    }

    def withoutTypeParams(s: String) = s.indexOf('[') match {
      case -1  => s
      case idx => s.substring(0, idx)
    }

    val paramText = param.calculateResult(context).toString
    withoutTypeParams(afterLatDot(paramText)) + Suffix
  }
}
