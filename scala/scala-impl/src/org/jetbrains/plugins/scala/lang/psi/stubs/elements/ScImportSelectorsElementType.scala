package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, StubInputStream, StubOutputStream}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportSelectors
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.imports.ScImportSelectorsImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScImportSelectorsStub
import org.jetbrains.plugins.scala.lang.psi.stubs.factories.ScStubSerializingElementFactory
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScImportSelectorsStubImpl

final class ScImportSelectorsElementType extends ScStubElementType[ScImportSelectors]("import selectors") {
  override def createElement(node: ASTNode): ScImportSelectors = new ScImportSelectorsImpl(node)
}

final class ScImportSelectorsStubFactory(elementType: ScImportSelectorsElementType)
  extends ScStubSerializingElementFactory[ScImportSelectorsStub, ScImportSelectors](elementType) {
  override def serialize(stub: ScImportSelectorsStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeBoolean(stub.hasWildcard)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScImportSelectorsStub =
    new ScImportSelectorsStubImpl(parentStub, elementType, hasWildcard = dataStream.readBoolean)

  override def createStubImpl(selectors: ScImportSelectors, parentStub: StubElement[_ <: PsiElement]): ScImportSelectorsStub =
    new ScImportSelectorsStubImpl(parentStub, elementType, hasWildcard = selectors.hasWildcard)

  override def createPsi(stub: ScImportSelectorsStub): ScImportSelectors = new ScImportSelectorsImpl(stub)
}
