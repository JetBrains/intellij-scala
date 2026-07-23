package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, StubInputStream}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScEnumCases
import org.jetbrains.plugins.scala.lang.psi.impl.statements.ScEnumCasesImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScEnumCasesStub
import org.jetbrains.plugins.scala.lang.psi.stubs.factories.ScStubSerializingElementFactory
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScEnumCasesStubImpl

final class ScEnumCasesElementType extends ScStubElementType[ScEnumCases]("ScEnumCases") {
  override def createElement(node: ASTNode): ScEnumCases = new ScEnumCasesImpl(null, null, node)
}

final class ScEnumCasesStubFactory(elementType: ScEnumCasesElementType) extends ScStubSerializingElementFactory[ScEnumCasesStub, ScEnumCases](elementType) {
  override def createPsi(stub: ScEnumCasesStub): ScEnumCases =
    new ScEnumCasesImpl(stub, new ScEnumCasesElementType, null)

  override def createStubImpl(psi: ScEnumCases, parentStub: StubElement[_ <: PsiElement]): ScEnumCasesStub =
    new ScEnumCasesStubImpl(parentStub, elementType)

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScEnumCasesStub =
    new ScEnumCasesStubImpl(parentStub, elementType)
}
