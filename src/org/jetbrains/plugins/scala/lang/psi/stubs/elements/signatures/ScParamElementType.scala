package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements
package signatures

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
    val typeText: String = psi.typeElement match {
      case Some(t) => t.getText()
      case None => ""
    }
    new ScParameterStubImpl[ParentPsi](parentStub, this, psi.getName, typeText, psi.isStable, psi.isDefaultParam, psi.isRepeatedParameter)
  }

  def serialize(stub: ScParameterStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeName(stub.getName)
    dataStream.writeName(stub.getTypeText)
    dataStream.writeBoolean(stub.isStable)
    dataStream.writeBoolean(stub.isDefaultParam)
    dataStream.writeBoolean(stub.isRepeated)
  }

  def deserializeImpl(dataStream: StubInputStream, parentStub: Any): ScParameterStub = {
    val name = StringRef.toString(dataStream.readName)
    val parent = parentStub.asInstanceOf[StubElement[PsiElement]]
    val typeText = StringRef.toString(dataStream.readName)
    val stable = dataStream.readBoolean
    val default = dataStream.readBoolean
    val repeated = dataStream.readBoolean
    new ScParameterStubImpl(parent, this, name, typeText, stable, default, repeated)
  }

  def indexStub(stub: ScParameterStub, sink: IndexSink): Unit = {}
}