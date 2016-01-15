package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import _root_.org.jetbrains.plugins.scala.lang.psi.impl.base.ScModifierListImpl
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream}
import com.intellij.util.ArrayUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScModifierList
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScModifiersStubImpl

/**
 * User: Alexander Podkhalyuzin
 * Date: 21.01.2009
 */

class ScModifiersElementType(debugName: String)
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
    val modifiers: Array[String] = psi.getModifiersStrings
    new ScModifiersStubImpl(parentStub, this, if (modifiers.isEmpty) ArrayUtil.EMPTY_STRING_ARRAY else modifiers, psi.hasExplicitModifiers)
  }

  def deserializeImpl(dataStream: StubInputStream, parentStub: Any): ScModifiersStub = {
    val explicitModifiers = dataStream.readBoolean()
    val num = dataStream.readInt
    val modifiers =
      if (num == 0) ArrayUtil.EMPTY_STRING_ARRAY
      else {
        val mods = new Array[String](num)
        for (i <- 0 until num) mods(i) = dataStream.readName.toString
        mods
      }
    new ScModifiersStubImpl(parentStub.asInstanceOf[StubElement[PsiElement]], this, modifiers, explicitModifiers)
  }

  def indexStub(stub: ScModifiersStub, sink: IndexSink) {}
}