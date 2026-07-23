package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream, StubSerializingElementFactory}
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScPrimaryConstructorImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScPrimaryConstructorStub
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScPrimaryConstructorStubImpl

class ScPrimaryConstructorElementType extends ScalaStubBasedElementType[ScPrimaryConstructorStub, ScPrimaryConstructor](ScPrimaryConstructorElementType.DebugName) {
  override def createElement(node: ASTNode): ScPrimaryConstructor = new ScPrimaryConstructorImpl(node)
}

object ScPrimaryConstructorElementType {
  val DebugName = "primary constructor"
}

class ScPrimaryConstructorStubFactory(elementType: IElementType)
  extends StubSerializingElementFactory[ScPrimaryConstructorStub, ScPrimaryConstructor] {

  override def serialize(stub: ScPrimaryConstructorStub, dataStream: StubOutputStream): Unit = {}

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScPrimaryConstructorStub =
    new ScPrimaryConstructorStubImpl(parentStub, elementType)

  override def createStub(constructor: ScPrimaryConstructor, parentStub: StubElement[_ <: PsiElement]): ScPrimaryConstructorStub =
    ScStubElementType.Processing.run {
      new ScPrimaryConstructorStubImpl(parentStub, elementType)
    }

  override def createPsi(stub: ScPrimaryConstructorStub): ScPrimaryConstructor = new ScPrimaryConstructorImpl(stub)

  override def indexStub(stub: ScPrimaryConstructorStub, sink: IndexSink): Unit = {}

  override def getExternalId: String = s"scala.${ScPrimaryConstructorElementType.DebugName}"

  override def shouldCreateStub(node: ASTNode): Boolean = !ScStubElementType.isLocal(node)
}
