package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl


import api.base.ScAccessModifier
import api.statements.params.ScTypeParamClause
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubElement}
/**
 * User: Alexander Podkhalyuzin
 * Date: 17.06.2009
 */

class ScTypeParamClauseStubImpl[ParentPsi <: PsiElement](parent: StubElement[ParentPsi],
                                                  elemType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement])
        extends StubBaseWrapper[ScTypeParamClause](parent, elemType) with ScTypeParamClauseStub {
  private var typeParamClauseText: String = ""

  def this(parent: StubElement[ParentPsi],
           elemType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
           typeParamClauseText: String) = {
    this (parent, elemType.asInstanceOf[IStubElementType[StubElement[PsiElement], PsiElement]])
    this.typeParamClauseText = typeParamClauseText
  }

  def getTypeParamClauseText: String = typeParamClauseText
}