package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubElement}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameterClause

/**
 * User: Alexander Podkhalyuzin
 * Date: 19.10.2008
 */

class ScParamClauseStubImpl[ParentPsi <: PsiElement](parent: StubElement[ParentPsi],
                                                  elemType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement])
extends StubBaseWrapper[ScParameterClause](parent, elemType) with ScParamClauseStub {
  var implic: Boolean = false

  def this(parent: StubElement[ParentPsi],
           elemType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
           implic: Boolean) = {
    this (parent, elemType.asInstanceOf[IStubElementType[StubElement[PsiElement], PsiElement]])
    this.implic = implic
  }

  def isImplicit: Boolean = implic
}