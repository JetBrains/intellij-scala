package org.jetbrains.plugins.scala.lang.psi.stubs.factories.signatures

import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.impl.statements.params.ScParameterImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScParameterStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.signatures.ScParameterElementType

final class ScParameterStubFactory(elementType: ScParameterElementType) extends ScParamStubFactory(elementType) {
  override def createPsi(stub: ScParameterStub): ScParameter = new ScParameterImpl(stub)
}
