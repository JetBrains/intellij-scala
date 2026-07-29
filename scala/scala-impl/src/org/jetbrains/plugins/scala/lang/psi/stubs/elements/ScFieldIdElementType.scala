package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream, StubSerializingElementFactory}
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.psi.api.base.ScFieldId
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScFieldIdImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScFieldIdStub
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScFieldIdStubImpl

class ScFieldIdElementType extends ScalaStubBasedElementType[ScFieldIdStub, ScFieldId](ScFieldIdElementType.DebugName) {
  override def createElement(node: ASTNode): ScFieldId = new ScFieldIdImpl(node)
}

object ScFieldIdElementType {
  val DebugName = "field id"
}

class ScFieldIdStubFactory(elementType: IElementType)
  extends StubSerializingElementFactory[ScFieldIdStub, ScFieldId] {

  override def serialize(stub: ScFieldIdStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeName(stub.getName)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScFieldIdStub =
    new ScFieldIdStubImpl(parentStub, elementType, name = dataStream.readNameString())

  override def createStub(psi: ScFieldId, parentStub: StubElement[_ <: PsiElement]): ScFieldIdStub =
    ScStubElementType.Processing.run {
      new ScFieldIdStubImpl(parentStub, elementType, name = psi.name)
    }

  override def createPsi(stub: ScFieldIdStub): ScFieldId = new ScFieldIdImpl(stub)

  override def indexStub(stub: ScFieldIdStub, sink: IndexSink): Unit = {}

  override def getExternalId: String = s"scala.${ScFieldIdElementType.DebugName}"

  override def shouldCreateStub(node: ASTNode): Boolean = !ScStubElementType.isLocal(node)
}
