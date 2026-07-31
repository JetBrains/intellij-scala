package org.jetbrains.plugins.scala.lang.psi.stubs.factories.signatures

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, StubInputStream, StubOutputStream}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameters
import org.jetbrains.plugins.scala.lang.psi.impl.statements.params.ScParametersImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScParamClausesStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.signatures.ScParamClausesElementType
import org.jetbrains.plugins.scala.lang.psi.stubs.factories.ScStubSerializingElementFactory
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScParamClausesStubImpl

final class ScParamClausesStubFactory(elementType: ScParamClausesElementType)
  extends ScStubSerializingElementFactory[ScParamClausesStub, ScParameters](elementType) {

  override def serialize(stub: ScParamClausesStub, dataStream: StubOutputStream): Unit = {}

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScParamClausesStub =
    new ScParamClausesStubImpl(parentStub, elementType)

  override def createStubImpl(psi: ScParameters, parentStub: StubElement[_ <: PsiElement]): ScParamClausesStub =
    new ScParamClausesStubImpl(parentStub, elementType)

  override def createPsi(stub: ScParamClausesStub): ScParameters = new ScParametersImpl(stub)
}
