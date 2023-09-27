package org.jetbrains.plugins.scala.codeInsight.template.macros

import com.intellij.codeInsight.template.{Expression, ExpressionContext, Result, TextResult}
import org.apache.commons.lang3.StringUtils
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightBundle
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScParameterizedTypeElement, ScTypeElement}

final class ScalaImplicitClassNameMacro extends ScalaMacro {

  override def getNameShort: String = "implicitValueClassName"

  override def getPresentableName: String = ScalaCodeInsightBundle.message("macro.implicit.value.class.name")

  import ScalaCodeStyleSettings.{DEFAULT_IMPLICIT_VALUE_CLASS_SUFFIX => DefaultSuffix}

  override def calculateResult(params: Array[Expression], context: ExpressionContext): Result = {
    val targetTypeName = params match {
      case Array(p) if p != null => p
      case _                     => return null
    }

    val name = calculateName(targetTypeName)(context)
    new TextResult(name)
  }

  override def calculateQuickResult(params: Array[Expression], context: ExpressionContext): Result = {
    calculateResult(params, context)
  }

  private def prefixAndSuffix(implicit context: ExpressionContext): (String, String) = {
    val p = ScalaCodeStyleSettings.getInstance(context.getProject).IMPLICIT_VALUE_CLASS_PREFIX
    val s = ScalaCodeStyleSettings.getInstance(context.getProject).IMPLICIT_VALUE_CLASS_SUFFIX
    (StringUtils.isBlank(p), StringUtils.isBlank(s)) match {
      case (false, false) => (p, s)
      case (false, true)  => (p, "")
      case (true, false)  => ("", s)
      case (true, true)   => ("", DefaultSuffix)
    }
  }

  private def calculateName(targetTypeName: Expression)(implicit context: ExpressionContext): String = {
    val typeElement = scTypeElement(targetTypeName)(context)
    val (prefix, suffix) = prefixAndSuffix
    val withSuffix = typeElement
      .map(appendSuffixToType(_, suffix))
      .getOrElse(targetTypeName.calculateResult(context).toString + suffix)
    prefix + withSuffix
  }

  private def appendSuffixToType(typeElement: ScTypeElement, suffix: String)(implicit context: ExpressionContext): String =
    typeElement match {
      case generic: ScParameterizedTypeElement =>
        val typeParams = collectGenericParamNames(generic)
        val typeParamsText = if (typeParams.isEmpty) "" else s"[${typeParams.mkString(", ")}]"
        generic.typeElement.getText + suffix + typeParamsText
      case typ =>
        typ.getText + suffix
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
    val (prefix, suffix) = prefixAndSuffix
    prefix + withoutTypeParams(afterLatDot(paramText)) + suffix
  }
}
