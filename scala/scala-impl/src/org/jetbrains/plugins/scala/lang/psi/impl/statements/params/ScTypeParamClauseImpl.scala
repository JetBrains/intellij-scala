package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements
package params

import com.intellij.lang.ASTNode
import com.intellij.psi._
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.plugins.scala.JavaArrayFactoryUtil.ScTypeParamFactory
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScMethodLike
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTypeParamClauseStub
import org.jetbrains.plugins.scala.lang.resolve.processor.BaseProcessor

class ScTypeParamClauseImpl private (stub: ScTypeParamClauseStub, node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, TYPE_PARAM_CLAUSE, node) with ScTypeParamClause {

  def this(node: ASTNode) = this(null, node)

  def this(stub: ScTypeParamClauseStub) = this(stub, null)

  override def toString: String = "TypeParameterClause"

  override def getTextByStub: String = byStubOrPsi(_.typeParameterClauseText)(getText)

  override def typeParameters: Seq[ScTypeParam] = getStubOrPsiChildren(TYPE_PARAM, ScTypeParamFactory).toSeq

  override def getTypeParameters: Array[PsiTypeParameter] = getStubOrPsiChildren(TYPE_PARAM, PsiTypeParameter.ARRAY_FACTORY)

  override def processDeclarations(processor: PsiScopeProcessor, state: ResolveState, lastParent: PsiElement, place: PsiElement): Boolean = {
    if (!processor.is[BaseProcessor] || isResolveInSyntheticClause(lastParent)) {
      for (param <- typeParameters) {
        if (!processor.execute(param, state)) return false
      }
    }
    true
  }

  override def deleteChildInternal(child: ASTNode): Unit = {
    val params = this.typeParameters
    val childIsTypeParam = params.exists(_.getNode == child)
    def childIsLastParamToBeDeleted = params.lengthIs == 1 && childIsTypeParam

    if (childIsLastParamToBeDeleted) this.delete()
    else if (childIsTypeParam) ScalaPsiUtil.deleteElementInCommaSeparatedList(this, child)
    else super.deleteChildInternal(child)
  }

  //scala syntax doesn't allow type parameters for constructors, but we need them for type inference, so they are synthetic
  //see ScMethodLike.getConstructorTypeParameters
  private def isSyntheticForConstructor: Boolean =
    getContext.asOptionOf[ScMethodLike].exists(_.isConstructor)

  private def isResolveInSyntheticClause(lastParent: PsiElement): Boolean =
    isSyntheticForConstructor && this.isAncestorOf(lastParent)
}
