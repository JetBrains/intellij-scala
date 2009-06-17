package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import _root_.org.jetbrains.plugins.scala.lang.psi.impl.base.ScModifierListImpl
import api.base.ScModifierList
import api.statements.ScFunction
import com.intellij.psi.stubs.{StubElement, IndexSink, StubOutputStream, StubInputStream}


import com.intellij.psi.PsiElement
import com.intellij.util.io.StringRef
import impl.ScModifiersStubImpl

/**
 * User: Alexander Podkhalyuzin
 * Date: 21.01.2009
 */

class ScModifiersElementType[Func <: ScModifierList](debugName: String)
        extends ScStubElementType[ScModifiersStub, ScModifierList](debugName) {
  def serialize(stub: ScModifiersStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeByte(stub.getModifiers.length)
    for (modifier <- stub.getModifiers) dataStream.writeName(modifier)
  }

  def createPsi(stub: ScModifiersStub): ScModifierList = {
    new ScModifierListImpl(stub)
  }

  def createStubImpl[ParentPsi <: PsiElement](psi: ScModifierList, parentStub: StubElement[ParentPsi]): ScModifiersStub = {
    new ScModifiersStubImpl(parentStub, this, psi.getModifiersStrings)
  }

  def deserializeImpl(dataStream: StubInputStream, parentStub: Any): ScModifiersStub = {
    val num = dataStream.readByte
    val modifiers = new Array[String](num)
    for (i <- 1 to num) modifiers(i-1) = StringRef.toString(dataStream.readName)
    new ScModifiersStubImpl(parentStub.asInstanceOf[StubElement[PsiElement]], this, modifiers)
  }

  def indexStub(stub: ScModifiersStub, sink: IndexSink): Unit = {}
}