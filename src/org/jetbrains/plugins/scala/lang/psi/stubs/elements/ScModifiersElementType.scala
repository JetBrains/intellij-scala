package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, StubInputStream, StubOutputStream}
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
    dataStream.writeNames(stub.modifiers)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScModifiersStub = {
    new ScModifiersStubImpl(parentStub, this,
      hasExplicitModifiers = dataStream.readBoolean,
      modifiersRefs = dataStream.readNames)
  }

  override def createStub(list: ScModifierList, parentStub: StubElement[_ <: PsiElement]): ScModifiersStub =
    new ScModifiersStubImpl(parentStub, this,
      hasExplicitModifiers = list.hasExplicitModifiers,
      modifiersRefs = list.modifiers.asReferences)

  override def createElement(node: ASTNode): ScModifierList = new ScModifierListImpl(node)

  override def createPsi(stub: ScModifiersStub): ScModifierList = new ScModifierListImpl(stub)
}