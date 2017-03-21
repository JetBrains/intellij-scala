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
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTypeParamClauseStub
import org.jetbrains.plugins.scala.lang.resolve.processor.BaseProcessor


/**
* @author Alexander Podkhalyuzin
* @since 22.02.2008
*/
class ScTypeParamClauseImpl private (stub: ScTypeParamClauseStub, node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, TYPE_PARAM_CLAUSE, node) with ScTypeParamClause {

  def this(node: ASTNode) = this(null, node)

  def this(stub: ScTypeParamClauseStub) = this(stub, null)

  override def toString: String = "TypeParameterClause"

  def getTextByStub: String = byStubOrPsi(_.typeParameterClauseText)(getText)

  def typeParameters: Seq[ScTypeParam] = getStubOrPsiChildren(TYPE_PARAM, ScTypeParamFactory)

  override def processDeclarations(processor: PsiScopeProcessor, state: ResolveState, lastParent: PsiElement, place: PsiElement): Boolean = {
    if (!processor.isInstanceOf[BaseProcessor]) {
      for (param <- typeParameters) {
        if (!processor.execute(param, state)) return false
      }
    }
    true
  }
}