package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, StubInputStream, StubOutputStream}
import com.intellij.util.ArrayUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScModifierList
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScModifierListImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScModifiersStubImpl

/**
  * User: Alexander Podkhalyuzin
  * Date: 21.01.2009
  */
class ScModifiersElementType(debugName: String)
  extends ScStubElementType[ScModifiersStub, ScModifierList](debugName) {
  override def serialize(stub: ScModifiersStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeBoolean(stub.hasExplicitModifiers)
    dataStream.writeInt(stub.getModifiers.length)
    for (modifier <- stub.getModifiers) dataStream.writeName(modifier)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScModifiersStub = {
    val explicitModifiers = dataStream.readBoolean()
    val num = dataStream.readInt
    val modifiers =
      if (num == 0) ArrayUtil.EMPTY_STRING_ARRAY
      else {
        val mods = new Array[String](num)
        for (i <- 0 until num) mods(i) = dataStream.readName.toString
        mods
      }
    new ScModifiersStubImpl(parentStub, this, modifiers, explicitModifiers)
  }

  override def createStub(psi: ScModifierList, parentStub: StubElement[_ <: PsiElement]): ScModifiersStub =
    new ScModifiersStubImpl(parentStub, this,
      psi.getModifiersStrings match {
        case Array() => ArrayUtil.EMPTY_STRING_ARRAY
        case array => array
      }, psi.hasExplicitModifiers)

  override def createElement(node: ASTNode): ScModifierList = new ScModifierListImpl(node)

  override def createPsi(stub: ScModifiersStub): ScModifierList = new ScModifierListImpl(stub)
}