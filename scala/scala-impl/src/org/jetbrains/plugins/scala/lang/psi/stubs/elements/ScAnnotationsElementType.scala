package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, StubInputStream, StubOutputStream}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotations
import org.jetbrains.plugins.scala.lang.psi.impl.expr.ScAnnotationsImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScAnnotationsStub
import org.jetbrains.plugins.scala.lang.psi.stubs.factories.ScStubSerializingElementFactory
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScAnnotationsStubImpl

class ScAnnotationsElementType extends ScStubElementType[ScAnnotations]("annotations") {
  override def createElement(node: ASTNode): ScAnnotations = new ScAnnotationsImpl(node)
}

final class ScAnnotationsStubFactory(elementType: ScAnnotationsElementType)
  extends ScStubSerializingElementFactory[ScAnnotationsStub, ScAnnotations](elementType) {
  override def serialize(stub: ScAnnotationsStub, dataStream: StubOutputStream): Unit = {}

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScAnnotationsStub =
    new ScAnnotationsStubImpl(parentStub, elementType)

  override def createStubImpl(psi: ScAnnotations, parentStub: StubElement[_ <: PsiElement]): ScAnnotationsStub =
    new ScAnnotationsStubImpl(parentStub, elementType)

  override def createPsi(stub: ScAnnotationsStub): ScAnnotations = new ScAnnotationsImpl(stub)
}
