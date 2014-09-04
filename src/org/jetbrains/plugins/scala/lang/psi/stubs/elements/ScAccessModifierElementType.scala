package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements


import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream}
import com.intellij.util.io.StringRef
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAccessModifier
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScAccessModifierImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScAccessModifierStubImpl

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.06.2009
 */

class ScAccessModifierElementType[Func <: ScAccessModifier]
        extends ScStubElementType[ScAccessModifierStub, ScAccessModifier]("access modifier") {
  def serialize(stub: ScAccessModifierStub, dataStream: StubOutputStream) {
    dataStream.writeBoolean(stub.isProtected)
    dataStream.writeBoolean(stub.isPrivate)
    dataStream.writeBoolean(stub.isThis)
    val hasId = stub.getIdText != None
    dataStream.writeBoolean(hasId)
    if (hasId) {
      dataStream.writeName(stub.getIdText.get)
    }
  }

  def indexStub(stub: ScAccessModifierStub, sink: IndexSink) {}

  def createPsi(stub: ScAccessModifierStub): ScAccessModifier = {
    new ScAccessModifierImpl(stub)
  }

  def createStubImpl[ParentPsi <: PsiElement](psi: ScAccessModifier, parentStub: StubElement[ParentPsi]): ScAccessModifierStub = {
    new ScAccessModifierStubImpl(parentStub.asInstanceOf[StubElement[PsiElement]], this, psi.isPrivate, psi.isProtected,
      psi.isThis, psi.idText.map(StringRef.fromString(_)))
  }

  def deserializeImpl(dataStream: StubInputStream, parentStub: Any): ScAccessModifierStub = {
    val isProtected = dataStream.readBoolean
    val isPrivate = dataStream.readBoolean
    val isThis = dataStream.readBoolean
    val hasId = dataStream.readBoolean
    val idText = if (hasId) Some(dataStream.readName)else None
    new ScAccessModifierStubImpl(parentStub.asInstanceOf[StubElement[PsiElement]], this, isPrivate, isProtected, isThis, idText)
  }
}