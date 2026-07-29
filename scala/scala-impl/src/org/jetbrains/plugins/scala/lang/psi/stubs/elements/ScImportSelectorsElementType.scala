package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream, StubSerializingElementFactory}
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportSelectors
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.imports.ScImportSelectorsImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScImportSelectorsStub
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScImportSelectorsStubImpl

class ScImportSelectorsElementType extends ScalaStubBasedElementType[ScImportSelectorsStub, ScImportSelectors](ScImportSelectorsElementType.DebugName) {
  override def createElement(node: ASTNode): ScImportSelectors = new ScImportSelectorsImpl(node)
}

object ScImportSelectorsElementType {
  val DebugName = "import selectors"
}

class ScImportSelectorsStubFactory(elementType: IElementType)
  extends StubSerializingElementFactory[ScImportSelectorsStub, ScImportSelectors] {
  override def serialize(stub: ScImportSelectorsStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeBoolean(stub.hasWildcard)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScImportSelectorsStub =
    new ScImportSelectorsStubImpl(parentStub, elementType, hasWildcard = dataStream.readBoolean)

  override def createStub(selectors: ScImportSelectors, parentStub: StubElement[_ <: PsiElement]): ScImportSelectorsStub =
    ScStubElementType.Processing.run {
      new ScImportSelectorsStubImpl(parentStub, elementType, hasWildcard = selectors.hasWildcard)
    }

  override def createPsi(stub: ScImportSelectorsStub): ScImportSelectors = new ScImportSelectorsImpl(stub)

  override def indexStub(stub: ScImportSelectorsStub, sink: IndexSink): Unit = {}

  override def getExternalId: String = s"scala.${ScImportSelectorsElementType.DebugName}"

  override def shouldCreateStub(node: ASTNode): Boolean = !ScStubElementType.isLocal(node)
}
