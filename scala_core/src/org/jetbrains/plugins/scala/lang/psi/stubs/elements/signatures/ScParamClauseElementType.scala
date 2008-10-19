package org.jetbrains.plugins.scala.lang.psi.stubs.elements.signatures

import _root_.org.jetbrains.plugins.scala.lang.psi.impl.statements.params.ScParameterClauseImpl
import api.statements.params.ScParameterClause
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, IndexSink, StubOutputStream, StubInputStream}
import impl.ScParamClauseStubImpl

/**
 * User: Alexander Podkhalyuzin
 * Date: 19.10.2008
 */

class ScParamClauseElementType
extends ScStubElementType[ScParamClauseStub, ScParameterClause]("parameter clause") {
  def serialize(stub: ScParamClauseStub, dataStream: StubOutputStream) {}

  def indexStub(stub: ScParamClauseStub, sink: IndexSink) {}

  protected def deserializeImpl(dataStream: StubInputStream, parentStub: Any): ScParamClauseStub = {
    new ScParamClauseStubImpl(parentStub.asInstanceOf[StubElement[PsiElement]], this)
  }

  protected def createStubImpl[ParentPsi <: PsiElement](psi: ScParameterClause, parentStub: StubElement[ParentPsi]) = {
    new ScParamClauseStubImpl(parentStub, this)     
  }

  def createPsi(stub: ScParamClauseStub): ScParameterClause = {
    new ScParameterClauseImpl(stub)
  }
}