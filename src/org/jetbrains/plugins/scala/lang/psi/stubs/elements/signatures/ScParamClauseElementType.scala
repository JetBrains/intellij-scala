package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements
package signatures

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
  def serialize(stub: ScParamClauseStub, dataStream: StubOutputStream) {
    dataStream.writeBoolean(stub.isImplicit)
  }

  def indexStub(stub: ScParamClauseStub, sink: IndexSink) {}

  def deserializeImpl(dataStream: StubInputStream, parentStub: Any): ScParamClauseStub = {
    val implic = dataStream.readBoolean
    new ScParamClauseStubImpl(parentStub.asInstanceOf[StubElement[PsiElement]], this, implic)
  }

  def createStubImpl[ParentPsi <: PsiElement](psi: ScParameterClause, parentStub: StubElement[ParentPsi]) = {
    new ScParamClauseStubImpl(parentStub, this, psi.isImplicit)     
  }

  def createPsi(stub: ScParamClauseStub): ScParameterClause = {
    new ScParameterClauseImpl(stub)
  }
}