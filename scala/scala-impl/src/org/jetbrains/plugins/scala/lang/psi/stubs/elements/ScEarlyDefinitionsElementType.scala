package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream, StubSerializingElementFactory}
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScEarlyDefinitions
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.ScEarlyDefinitionsImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScEarlyDefinitionsStub
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScEarlyDefinitionsStubImpl

class ScEarlyDefinitionsElementType
  extends ScalaStubBasedElementType[ScEarlyDefinitionsStub, ScEarlyDefinitions](ScEarlyDefinitionsElementType.DebugName) {
  override def createElement(node: ASTNode): ScEarlyDefinitions = new ScEarlyDefinitionsImpl(node)
}

object ScEarlyDefinitionsElementType {
  val DebugName = "early definitions"
}

class ScEarlyDefinitionsStubFactory(elementType: IElementType)
  extends StubSerializingElementFactory[ScEarlyDefinitionsStub, ScEarlyDefinitions] {

  override def serialize(stub: ScEarlyDefinitionsStub, dataStream: StubOutputStream): Unit = {}

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScEarlyDefinitionsStub =
    new ScEarlyDefinitionsStubImpl(parentStub, elementType)

  override def createStub(psi: ScEarlyDefinitions, parentStub: StubElement[_ <: PsiElement]): ScEarlyDefinitionsStub =
    ScStubElementType.Processing.run {
      new ScEarlyDefinitionsStubImpl(parentStub, elementType)
    }

  override def createPsi(stub: ScEarlyDefinitionsStub): ScEarlyDefinitions = new ScEarlyDefinitionsImpl(stub)

  override def indexStub(stub: ScEarlyDefinitionsStub, sink: IndexSink): Unit = {}

  override def getExternalId: String = s"scala.${ScEarlyDefinitionsElementType.DebugName}"

  override def shouldCreateStub(node: ASTNode): Boolean = !ScStubElementType.isLocal(node)
}
