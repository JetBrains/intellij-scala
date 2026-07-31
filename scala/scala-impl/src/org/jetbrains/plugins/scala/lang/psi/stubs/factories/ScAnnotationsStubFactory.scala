package org.jetbrains.plugins.scala.lang.psi.stubs.factories

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, StubInputStream, StubOutputStream}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotations
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScAnnotationsImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScAnnotationsStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScAnnotationsElementType
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScAnnotationsStubImpl

final class ScAnnotationsStubFactory(elementType: ScAnnotationsElementType)
  extends ScStubSerializingElementFactory[ScAnnotationsStub, ScAnnotations](elementType) {
  override def serialize(stub: ScAnnotationsStub, dataStream: StubOutputStream): Unit = {}

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScAnnotationsStub =
    new ScAnnotationsStubImpl(parentStub, elementType)

  override def createStubImpl(psi: ScAnnotations, parentStub: StubElement[_ <: PsiElement]): ScAnnotationsStub =
    new ScAnnotationsStubImpl(parentStub, elementType)

  override def createPsi(stub: ScAnnotationsStub): ScAnnotations = new ScAnnotationsImpl(stub)
}
