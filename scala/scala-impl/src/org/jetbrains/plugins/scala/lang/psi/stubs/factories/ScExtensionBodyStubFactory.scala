package org.jetbrains.plugins.scala.lang.psi.stubs.factories

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, StubInputStream}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScExtensionBody
import org.jetbrains.plugins.scala.lang.psi.impl.statements.ScExtensionBodyImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScExtensionBodyStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScExtensionBodyElementType
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScExtensionBodyStubImpl

final class ScExtensionBodyStubFactory(elementType: ScExtensionBodyElementType)
  extends ScStubSerializingElementFactory[ScExtensionBodyStub, ScExtensionBody](elementType) {

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScExtensionBodyStub =
    new ScExtensionBodyStubImpl(parentStub, elementType)

  override def createStubImpl(extBody: ScExtensionBody, parentStub: StubElement[_ <: PsiElement]): ScExtensionBodyStub =
    new ScExtensionBodyStubImpl(parentStub, elementType)

  override def createPsi(stub: ScExtensionBodyStub): ScExtensionBody = new ScExtensionBodyImpl(stub)
}
