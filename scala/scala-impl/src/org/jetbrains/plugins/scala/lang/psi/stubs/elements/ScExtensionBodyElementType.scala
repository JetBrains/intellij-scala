package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream, StubSerializingElementFactory}
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScExtensionBody
import org.jetbrains.plugins.scala.lang.psi.impl.statements.ScExtensionBodyImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScExtensionBodyStub
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScExtensionBodyStubImpl

class ScExtensionBodyElementType extends ScalaStubBasedElementType[ScExtensionBodyStub, ScExtensionBody](ScExtensionBodyElementType.DebugName) {
  override def createElement(node: ASTNode): ScExtensionBody = new ScExtensionBodyImpl(node)
}

object ScExtensionBodyElementType {
  val DebugName = "extension body"
}

class ScExtensionBodyStubFactory(elementType: IElementType)
  extends StubSerializingElementFactory[ScExtensionBodyStub, ScExtensionBody] {

  override def serialize(stub: ScExtensionBodyStub, dataStream: StubOutputStream): Unit = {}

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScExtensionBodyStub =
    new ScExtensionBodyStubImpl(parentStub, elementType)

  override def createStub(extBody: ScExtensionBody, parentStub: StubElement[_ <: PsiElement]): ScExtensionBodyStub =
    ScStubElementType.Processing.run {
      new ScExtensionBodyStubImpl(parentStub, elementType)
    }

  override def createPsi(stub: ScExtensionBodyStub): ScExtensionBody = new ScExtensionBodyImpl(stub)

  override def indexStub(stub: ScExtensionBodyStub, sink: IndexSink): Unit = {}

  override def getExternalId: String = s"scala.${ScExtensionBodyElementType.DebugName}"

  override def shouldCreateStub(node: ASTNode): Boolean = !ScStubElementType.isLocal(node)
}
