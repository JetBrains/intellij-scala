package org.jetbrains.plugins.scala.lang.psi.stubs.factories

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, StubInputStream}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScPrimaryConstructorImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScPrimaryConstructorStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScPrimaryConstructorElementType
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScPrimaryConstructorStubImpl

final class ScPrimaryConstructorStubFactory(elementType: ScPrimaryConstructorElementType)
  extends ScStubSerializingElementFactory[ScPrimaryConstructorStub, ScPrimaryConstructor](elementType) {
  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScPrimaryConstructorStub =
    new ScPrimaryConstructorStubImpl(parentStub, elementType)

  override def createStubImpl(constructor: ScPrimaryConstructor, parentStub: StubElement[_ <: PsiElement]): ScPrimaryConstructorStub =
    new ScPrimaryConstructorStubImpl(parentStub, elementType)

  override def createPsi(stub: ScPrimaryConstructorStub): ScPrimaryConstructor = new ScPrimaryConstructorImpl(stub)
}
