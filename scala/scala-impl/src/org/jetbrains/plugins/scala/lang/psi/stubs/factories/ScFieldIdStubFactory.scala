package org.jetbrains.plugins.scala.lang.psi.stubs.factories

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, StubInputStream, StubOutputStream}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScFieldId
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScFieldIdImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScFieldIdStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScFieldIdElementType
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScFieldIdStubImpl

final class ScFieldIdStubFactory(elementType: ScFieldIdElementType)
  extends ScStubSerializingElementFactory[ScFieldIdStub, ScFieldId](elementType) {

  override def serialize(stub: ScFieldIdStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeName(stub.getName)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScFieldIdStub =
    new ScFieldIdStubImpl(parentStub, elementType, name = dataStream.readNameString())

  override def createStubImpl(psi: ScFieldId, parentStub: StubElement[_ <: PsiElement]): ScFieldIdStub =
    new ScFieldIdStubImpl(parentStub, elementType, name = psi.name)

  override def createPsi(stub: ScFieldIdStub): ScFieldId = new ScFieldIdImpl(stub)
}
