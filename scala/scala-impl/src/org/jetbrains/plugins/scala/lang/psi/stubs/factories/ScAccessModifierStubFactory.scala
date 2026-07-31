package org.jetbrains.plugins.scala.lang.psi.stubs.factories

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, StubInputStream, StubOutputStream}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAccessModifier
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScAccessModifierImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScAccessModifierStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScAccessModifierElementType
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScAccessModifierStubImpl

final class ScAccessModifierStubFactory(elementType: ScAccessModifierElementType)
  extends ScStubSerializingElementFactory[ScAccessModifierStub, ScAccessModifier](elementType) {

  override def serialize(stub: ScAccessModifierStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeBoolean(stub.isProtected)
    dataStream.writeBoolean(stub.isPrivate)
    dataStream.writeBoolean(stub.isThis)
    dataStream.writeOptionName(stub.idText)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScAccessModifierStub =
    new ScAccessModifierStubImpl(parentStub, elementType,
      isProtected = dataStream.readBoolean,
      isPrivate = dataStream.readBoolean,
      isThis = dataStream.readBoolean,
      idText = dataStream.readOptionName)

  override def createStubImpl(modifier: ScAccessModifier, parentStub: StubElement[_ <: PsiElement]): ScAccessModifierStub =
    new ScAccessModifierStubImpl(parentStub, elementType,
      isProtected = modifier.isProtected,
      isPrivate = modifier.isPrivate,
      isThis = modifier.isThis,
      idText = modifier.idText
    )

  override def createPsi(stub: ScAccessModifierStub): ScAccessModifier = new ScAccessModifierImpl(stub)
}
