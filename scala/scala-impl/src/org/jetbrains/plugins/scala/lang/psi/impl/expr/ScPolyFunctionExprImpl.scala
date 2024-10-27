package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.lang.ASTNode
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.{PsiElement, ResolveState}
import org.jetbrains.plugins.scala.caches.{ModTracker, cached}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScPolyFunctionExpr}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameterClause
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.types.ScParameterizedType
import org.jetbrains.plugins.scala.lang.psi.types.api.{TypeParameter, TypeParameterType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.ScTypePolymorphicType
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult

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

  protected override def innerType: TypeResult = {
    val contextBounds =
      typeParameters.flatMap {
        typeParam =>
          typeParam.contextBound.map(
            boundType => ScParameterizedType(boundType, Seq(TypeParameterType(typeParam)))
          )
      }

    val resultType = {
      val inner = this.flatMapType(result).getOrAny

      if (contextBounds.isEmpty)
        inner
      else
        ScPolyFunctionExpr.propagateContextBounds(inner, contextBounds)
    }

    Right(ScTypePolymorphicType(resultType, typeParameters.map(TypeParameter(_))))
  }

  override def controlFlowScope: Option[ScalaPsiElement] = result

  override def toString: String = "PolyFunctionExpression"
}
