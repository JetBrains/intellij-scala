package org.jetbrains.plugins.scala.codeInsight.template.macros

import com.intellij.codeInsight.template.{Expression, ExpressionContext, PsiElementResult, Result, TextResult}
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightBundle
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner

final class ScalaTypeParametersMacro extends ScalaMacro {

  override def getNameShort: String = "typeParams"

  override def getPresentableName: String = ScalaCodeInsightBundle.message("marco.typeParameters")

  override def calculateResult(params: Array[Expression], context: ExpressionContext): Result = {
    val owner = ScalaTypeParametersMacro.typeOwner(params, context).getOrElse(return null)
    val result = owner.typeParametersClause.map(_.getText).getOrElse("")
    new TextResult(result)
  }
}

object ScalaTypeParametersMacro {

  private[macros] def typeOwner(params: Array[Expression], context: ExpressionContext): Option[ScTypeParametersOwner] =
    params match {
      case Array(clazzNode) =>
        clazzNode.calculateResult(context) match {
          case elementResult: PsiElementResult =>
            elementResult.getElement match {
              case owner: ScTypeParametersOwner => Some(owner)
              case _ => None
            }
          case _ => None
        }
      case _ => None
    }
}

final class ScalaTypeParametersWithoutBoundsMacro extends ScalaMacro {

  override def getNameShort: String = "typeParamsWithoutBounds"

  override def getPresentableName: String = ScalaCodeInsightBundle.message("marco.typeParameters.without.bounds")

  override def calculateResult(params: Array[Expression], context: ExpressionContext): Result = {
    val owner = ScalaTypeParametersMacro.typeOwner(params, context).getOrElse(return null)
    val typeParams = owner.typeParameters.map(_.nameId).map(_.getText)
    val result =
      if (typeParams.isEmpty) ""
      else typeParams.mkString("[", ", ", "]")
    new TextResult(result)
  }
}
