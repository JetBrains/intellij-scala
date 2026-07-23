package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream, StubSerializingElementFactory}
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAccessModifier
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScAccessModifierImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScAccessModifierStub
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScAccessModifierStubImpl

class ScAccessModifierElementType extends ScalaStubBasedElementType[ScAccessModifierStub, ScAccessModifier](ScAccessModifierElementType.DebugName) {
  override def createElement(node: ASTNode): ScAccessModifier = new ScAccessModifierImpl(node)
}

object ScAccessModifierElementType {
  val DebugName = "access modifier"
}

class ScAccessModifierStubFactory(elementType: IElementType)
  extends StubSerializingElementFactory[ScAccessModifierStub, ScAccessModifier] {

  override def serialize(stub: ScAccessModifierStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeBoolean(stub.isProtected)
    dataStream.writeBoolean(stub.isPrivate)
    dataStream.writeBoolean(stub.isThis)
    dataStream.writeOptionName(stub.idText)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScAccessModifierStub =
    new ScAccessModifierStubImpl(parentStub, elementType,
      isProtected = dataStream.readBoolean,
      isPrivate = dataStream.readBoolean,
      isThis = dataStream.readBoolean,
      idText = dataStream.readOptionName)

  override def createStub(modifier: ScAccessModifier, parentStub: StubElement[_ <: PsiElement]): ScAccessModifierStub =
    ScStubElementType.Processing.run {
      new ScAccessModifierStubImpl(parentStub, elementType,
        isProtected = modifier.isProtected,
        isPrivate = modifier.isPrivate,
        isThis = modifier.isThis,
        idText = modifier.idText)
    }

  override def createPsi(stub: ScAccessModifierStub): ScAccessModifier = new ScAccessModifierImpl(stub)

  override def indexStub(stub: ScAccessModifierStub, sink: IndexSink): Unit = {}

  override def getExternalId: String = s"scala.${ScAccessModifierElementType.DebugName}"

  override def shouldCreateStub(node: ASTNode): Boolean = !ScStubElementType.isLocal(node)
}
