package org.jetbrains.plugins.scala.lang.psi.api.toplevel

import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import com.intellij.psi._
import com.intellij.psi.scope.PsiScopeProcessor

trait ScTypeParametersOwner extends ScalaPsiElement {
  def typeParameters() : Seq[ScTypeParam] = typeParametersClause.typeParameters

  def typeParametersClause =  findChildByClass(classOf[ScTypeParamClause])

  override def processDeclarations(processor: PsiScopeProcessor,
                                  state: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement): Boolean = {
    for (tp <- typeParameters) {
      if (!processor.execute(tp, state)) return false
    }
    true
  }
}