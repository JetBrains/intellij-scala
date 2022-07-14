package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.{PsiElement, ResolveState}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScPolyFunctionExpr}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeParameter
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.ScTypePolymorphicType
import org.jetbrains.plugins.scala.lang.psi.types.result._

class ScPolyFunctionExprImpl(node: ASTNode)
    extends ScExpressionImplBase(node)
    with ScPolyFunctionExpr
    with ScTypeParametersOwner {

  override def result: Option[ScExpression] = findChild[ScExpression]

  override def processDeclarations(processor: PsiScopeProcessor,
                                   state: ResolveState,
                                   lastParent: PsiElement,
                                   place: PsiElement): Boolean = {
    if (lastParent != null) {
      if (!super[ScTypeParametersOwner].processDeclarations(processor, state, lastParent, place))
        return false
    }
    true
  }

  protected override def innerType: TypeResult = {
    val resultType = this.flatMapType(result).getOrAny
    Right(ScTypePolymorphicType(resultType, typeParameters.map(TypeParameter(_))))
  }

  override def controlFlowScope: Option[ScalaPsiElement] = result

  override def toString: String = "PolyFunctionExpression"
}
