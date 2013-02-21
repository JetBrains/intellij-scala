package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

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
  def serialize(stub: ScModifiersStub, dataStream: StubOutputStream) {
    dataStream.writeBoolean(stub.hasExplicitModifiers)
    dataStream.writeInt(stub.getModifiers.length)
    for (modifier <- stub.getModifiers) dataStream.writeName(modifier)
  }

  def createPsi(stub: ScModifiersStub): ScModifierList = {
    new ScModifierListImpl(stub)
  }

  def createStubImpl[ParentPsi <: PsiElement](psi: ScModifierList, parentStub: StubElement[ParentPsi]): ScModifiersStub = {
    new ScModifiersStubImpl(parentStub, this, psi.getModifiersStrings, psi.hasExplicitModifiers)
  }

  def deserializeImpl(dataStream: StubInputStream, parentStub: Any): ScModifiersStub = {
    val explicitModifiers = dataStream.readBoolean()
    val num = dataStream.readInt
    val modifiers = new Array[String](num)
    for (i <- 0 until num) modifiers(i) = dataStream.readName.toString
    new ScModifiersStubImpl(parentStub.asInstanceOf[StubElement[PsiElement]], this, modifiers, explicitModifiers)
  }

  def indexStub(stub: ScModifiersStub, sink: IndexSink) {}
}