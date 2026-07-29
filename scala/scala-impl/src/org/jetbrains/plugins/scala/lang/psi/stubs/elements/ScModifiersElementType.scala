package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream, StubSerializingElementFactory}
import org.jetbrains.plugins.scala.lang.lexer.ScalaModifier
import org.jetbrains.plugins.scala.lang.psi.api.base.ScModifierList
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScModifierListImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScModifiersStub
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScModifiersStubImpl
import org.jetbrains.plugins.scala.util.EnumSet

/**
 * A plain [[ScalaStubBasedElementType]] that only creates PSI from AST.
 * Stub building/serialization lives in [[ScModifiersStubFactory]]
 */
class ScModifiersElementType(debugName: String)
  extends ScalaStubBasedElementType[ScModifiersStub, ScModifierList](debugName) {
  override def createElement(node: ASTNode) = new ScModifierListImpl(node)
}

class ScModifiersStubFactory(elementType: ScModifiersElementType)
  extends StubSerializingElementFactory[ScModifiersStub, ScModifierList] {

  override def serialize(stub: ScModifiersStub, dataStream: StubOutputStream): Unit =
    dataStream.writeInt(stub.modifiers)

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScModifiersStub =
    new ScModifiersStubImpl(
      parentStub,
      elementType,
      modifiers = EnumSet.readFromInt[ScalaModifier](dataStream.readInt)
    )

  override def indexStub(stub: ScModifiersStub, sink: IndexSink): Unit = {}

  override def createStub(psi: ScModifierList, parentStub: StubElement[_ <: PsiElement]): ScModifiersStub =
    ScStubElementType.Processing.run {
      new ScModifiersStubImpl(parentStub, elementType, modifiers = psi.modifiers)
    }

  override def createPsi(stub: ScModifiersStub): ScModifierList = new ScModifierListImpl(stub)

  // Preserves the former `getLanguage.getDisplayName.toLowerCase + "." + debugName` external id.
  override def getExternalId: String = "scala.modifiers"

  // Preserves the former ScStubElementType.shouldCreateStub behavior (skip stubs for local elements).
  override def shouldCreateStub(node: ASTNode): Boolean = !ScStubElementType.isLocal(node)
}
