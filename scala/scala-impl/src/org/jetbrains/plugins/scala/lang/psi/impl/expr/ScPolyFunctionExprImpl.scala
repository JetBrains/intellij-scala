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
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScTypeParam, ScTypeParamClause}
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, TypeResult}

class ScPolyFunctionExprImpl(node: ASTNode) extends ScExpressionImplBase(node) with ScPolyFunctionExpr {

  override def typeParameters : Seq[ScTypeParam] = typeParamClause.typeParameters

  override def typeParamClause: ScTypeParamClause = findChildByClass(classOf[ScTypeParamClause])

  override def result: Option[ScExpression] = findChild(classOf[ScExpression])

  override def processDeclarations(processor: PsiScopeProcessor,
                                   state: ResolveState,
                                   lastParent: PsiElement,
                                   place: PsiElement): Boolean = {
    //result match {
    //  case Some(x) if x == lastParent || (lastParent.isInstanceOf[ScalaPsiElement] &&
    //    x == lastParent.asInstanceOf[ScalaPsiElement].getDeepSameElementInContext) =>
    //    for (p <- parameters) {
    //      if (!processor.execute(p, state)) return false
    //    }
    //    true
    //  case _ => true
    //}
    // todo: implement this!
    true
  }

  protected override def innerType: TypeResult = {
    // todo: implement this!
    Failure(NlsString.force("not implemented"))
  }

  override def controlFlowScope: Option[ScalaPsiElement] = result

  override def toString: String = "PolyFunctionExpression"
}
