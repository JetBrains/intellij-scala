package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.lang.ASTNode
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.{PsiElement, ResolveState}
import org.jetbrains.plugins.scala.caches.{ModTracker, cached}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScParenthesizedElement.InnermostElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScFunctionExpr, ScPolyFunctionExpr}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameterClause
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.result.{OptionTypeExt, TypeResult}
import org.jetbrains.plugins.scala.lang.psi.types.{Context, ScType, TypePresentationContext}

class ScPolyFunctionExprImpl(node: ASTNode)
  extends ScExpressionImplBase(node)
  with ScPolyFunctionExpr
  with ScTypeParametersOwner {

  override def result: Option[ScExpression] = findChild[ScExpression]

  override def processDeclarations(
    processor:  PsiScopeProcessor,
    state:      ResolveState,
    lastParent: PsiElement,
    place:      PsiElement
  ): Boolean = {
    if (lastParent != null) {
      if (!super[ScTypeParametersOwner].processDeclarations(processor, state, lastParent, place))
        return false
    }

    syntheticContextBoundParameters().foreach {
      clause =>
        clause.parameters.foreach(param =>
          if (!processor.execute(param, state)) return false
        )
    }

    true
  }

  private val syntheticContextBoundParameters: () => Option[ScParameterClause] =
    cached(
      "ScPolyFunctionExpr#syntheticContextBoundParameters",
      ModTracker.anyScalaPsiChange,
      () => {
        ScalaPsiUtil
          .syntheticParamClause(
            typeParameters,
            this,
            isClassParameter = false,
            hasImplicit = false
          )
      }
    )

  private val cachedDesugaredType: () => Option[ScType] =
    cached(
      "ScPolyFunctionExpr#cachedDesugaredType",
      ModTracker.anyScalaPsiChange,
      () => {
        implicit val tpc: TypePresentationContext = this

        val typeParamsText = typeParametersClause.fold("") { tParamClause =>
          val tParams = tParamClause.typeParameters

          tParams.zipWithIndex.map { case (tParam, idx) =>
            val text = tParam.typeParameterText
            if (text == "_") s"$text$$$idx"
            else             text
          }.mkString("[", ", ", "]")
        }

        val (paramsText, resultTypeText) = result match {
          case Some(InnermostElement(fn: ScFunctionExpr)) =>
            val text = fn.parameters.map { p =>
              val typeText = {
                val text = p.`type`().getOrAny.presentableText
                if (text == "_") "_$0"
                else             text
              }

              s"${p.name}: $typeText"
            }.mkString("(", ", ", ")")

            text ->
              this.flatMapType(fn.result).getOrAny.presentableText(this, Context.Empty)
          case _ => "()" -> "scala.Any"
        }

        ScalaPsiElementFactory.createTypeFromText(
          s"""
             |scala.PolyFunction {
             |  def apply$typeParamsText$paramsText: $resultTypeText
             |}
             |""".stripMargin, getContext, null
        )
      }
    )

  protected override def innerType: TypeResult =
    cachedDesugaredType().asTypeResult

  override def controlFlowScope: Option[ScalaPsiElement] = result

  override def toString: String = "PolyFunctionExpression"
}
