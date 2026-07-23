package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, StubInputStream, StubOutputStream}
import org.jetbrains.plugins.scala.lang.lexer.ScalaModifier
import org.jetbrains.plugins.scala.lang.psi.api.base.ScModifierList
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScModifierListImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScModifiersStub
import org.jetbrains.plugins.scala.lang.psi.stubs.factories.ScStubSerializingElementFactory
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScModifiersStubImpl
import org.jetbrains.plugins.scala.util.EnumSet

final class ScModifiersElementType extends ScStubElementType[ScModifierList]("modifiers") {
  override def createElement(node: ASTNode) = new ScModifierListImpl(node)
}

final class ScModifiersStubFactory(elementType: ScModifiersElementType)
  extends ScStubSerializingElementFactory[ScModifiersStub, ScModifierList](elementType) {

  override def serialize(stub: ScModifiersStub, dataStream: StubOutputStream): Unit =
    dataStream.writeInt(stub.modifiers)

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScModifiersStub =
    new ScModifiersStubImpl(
      parentStub,
      elementType,
      modifiers = EnumSet.readFromInt[ScalaModifier](dataStream.readInt)
    )

  override def createStubImpl(psi: ScModifierList, parentStub: StubElement[_ <: PsiElement]): ScModifiersStub =
    new ScModifiersStubImpl(parentStub, elementType, modifiers = psi.modifiers)

  override def createPsi(stub: ScModifiersStub): ScModifierList = new ScModifierListImpl(stub)
}
