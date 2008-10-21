package org.jetbrains.plugins.scala.lang.psi.stubs.elements.signatures

import api.statements.params.{ScClassParameter, ScParameter}
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, IndexSink, StubOutputStream, StubInputStream}
import com.intellij.util.io.StringRef
import impl.ScParameterStubImpl

/**
 * User: Alexander Podkhalyuzin
 * Date: 19.10.2008
 */

abstract class ScParamElementType[Param <: ScParameter](debugName: String)
extends ScStubElementType[ScParameterStub, ScParameter](debugName) {
  def createStubImpl[ParentPsi <: PsiElement](psi: ScParameter, parentStub: StubElement[ParentPsi]): ScParameterStub = {
    new ScParameterStubImpl[ParentPsi](parentStub, this, psi.getName)
  }

  def serialize(stub: ScParameterStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeName(stub.getName)
  }

  def deserializeImpl(dataStream: StubInputStream, parentStub: Any): ScParameterStub = {
    val name = StringRef.toString(dataStream.readName)
    val parent = parentStub.asInstanceOf[StubElement[PsiElement]]
    new ScParameterStubImpl(parent, this, name)
  }

  def indexStub(stub: ScParameterStub, sink: IndexSink): Unit = {}
}