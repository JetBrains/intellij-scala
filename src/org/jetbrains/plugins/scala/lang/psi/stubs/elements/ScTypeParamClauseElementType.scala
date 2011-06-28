package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements


import api.statements.params.ScTypeParamClause
import psi.impl.statements.params.ScTypeParamClauseImpl
import com.intellij.psi.stubs.{StubOutputStream, IndexSink, StubElement, StubInputStream}


import com.intellij.psi.PsiElement
import impl.ScTypeParamClauseStubImpl

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.06.2009
 */

class ScTypeParamClauseElementType[Func <: ScTypeParamClause]
        extends ScStubElementType[ScTypeParamClauseStub, ScTypeParamClause]("type parameter clause") {
  def serialize(stub: ScTypeParamClauseStub, dataStream: StubOutputStream) {
    dataStream.writeName(stub.getTypeParamClauseText)
  }

  def indexStub(stub: ScTypeParamClauseStub, sink: IndexSink) {}

  def createPsi(stub: ScTypeParamClauseStub): ScTypeParamClause = {
    new ScTypeParamClauseImpl(stub)
  }

  def createStubImpl[ParentPsi <: PsiElement](psi: ScTypeParamClause, parentStub: StubElement[ParentPsi]): ScTypeParamClauseStub = {
    new ScTypeParamClauseStubImpl(parentStub.asInstanceOf[StubElement[PsiElement]], this, psi.getText)
  }

  def deserializeImpl(dataStream: StubInputStream, parentStub: Any): ScTypeParamClauseStub = {
    val text = dataStream.readName().toString
    new ScTypeParamClauseStubImpl(parentStub.asInstanceOf[StubElement[PsiElement]], this, text)
  }
}