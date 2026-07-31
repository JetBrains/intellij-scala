package org.jetbrains.plugins.scala.lang.psi.stubs.factories

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, StubInputStream}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScDerivesClause
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.templates.ScDerivesClauseImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScDerivesClauseStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScDerivesClauseElementType
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScDerivesClauseStubImpl

final class ScDerivesClauseStubFactory(elementType: ScDerivesClauseElementType)
  extends ScStubSerializingElementFactory[ScDerivesClauseStub, ScDerivesClause](elementType) {

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScDerivesClauseStub =
    new ScDerivesClauseStubImpl(parentStub, elementType)

  override def createStubImpl(psi: ScDerivesClause, parentStub: StubElement[_ <: PsiElement]): ScDerivesClauseStub =
    new ScDerivesClauseStubImpl(parentStub, elementType)

  override def createPsi(stub: ScDerivesClauseStub): ScDerivesClause = new ScDerivesClauseImpl(stub)
}
