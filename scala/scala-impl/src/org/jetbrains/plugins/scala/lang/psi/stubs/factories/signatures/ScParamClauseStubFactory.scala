package org.jetbrains.plugins.scala.lang.psi.stubs.factories.signatures

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, StubInputStream, StubOutputStream}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameterClause
import org.jetbrains.plugins.scala.lang.psi.impl.statements.params.ScParameterClauseImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScParamClauseStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.signatures.ScParamClauseElementType
import org.jetbrains.plugins.scala.lang.psi.stubs.factories.ScStubSerializingElementFactory
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScParamClauseStubImpl

final class ScParamClauseStubFactory(elementType: ScParamClauseElementType)
  extends ScStubSerializingElementFactory[ScParamClauseStub, ScParameterClause](elementType) {

  override def serialize(stub: ScParamClauseStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeBoolean(stub.hasImplicitKeyword)
    dataStream.writeBoolean(stub.hasUsingKeyword)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScParamClauseStub =
    new ScParamClauseStubImpl(parentStub, elementType,
      hasImplicitKeyword = dataStream.readBoolean,
      hasUsingKeyword = dataStream.readBoolean,
    )

  override def createStubImpl(parameterClause: ScParameterClause, parentStub: StubElement[_ <: PsiElement]): ScParamClauseStub =
    new ScParamClauseStubImpl(parentStub, elementType,
      hasImplicitKeyword = parameterClause.hasImplicitKeyword,
      hasUsingKeyword = parameterClause.hasUsingKeyword,
    )

  override def createPsi(stub: ScParamClauseStub): ScParameterClause = new ScParameterClauseImpl(stub)
}
