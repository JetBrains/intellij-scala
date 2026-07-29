package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream, StubSerializingElementFactory}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSelfTypeElement
import org.jetbrains.plugins.scala.lang.psi.impl.base.types.ScSelfTypeElementImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScSelfTypeElementStub
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScSelfTypeElementStubImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys

/**
 * A plain [[ScalaStubBasedElementType]] that only creates PSI from AST.
 * Stub building/serialization lives in [[ScSelfTypeElementStubFactory]]
 */
class ScSelfTypeElementElementType
  extends ScalaStubBasedElementType[ScSelfTypeElementStub, ScSelfTypeElement](ScSelfTypeElementElementType.DebugName) {
  override def createElement(node: ASTNode) = new ScSelfTypeElementImpl(node)
}

object ScSelfTypeElementElementType {
  val DebugName = "self type element"
}

class ScSelfTypeElementStubFactory(elementType: ScSelfTypeElementElementType)
  extends StubSerializingElementFactory[ScSelfTypeElementStub, ScSelfTypeElement] {

  override def serialize(stub: ScSelfTypeElementStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeName(stub.getName)
    dataStream.writeOptionName(stub.typeText)
    dataStream.writeNames(stub.classNames)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScSelfTypeElementStub =
    new ScSelfTypeElementStubImpl(
      parentStub,
      elementType,
      name = dataStream.readNameString,
      typeText = dataStream.readOptionName,
      classNames = dataStream.readNames
    )

  override def createStub(typeElement: ScSelfTypeElement, parentStub: StubElement[_ <: PsiElement]): ScSelfTypeElementStub =
    ScStubElementType.Processing.run {
      new ScSelfTypeElementStubImpl(
        parentStub,
        elementType,
        name = typeElement.name,
        typeText = typeElement.typeElement.map(_.getText),
        classNames = typeElement.classNames
      )
    }

  override def indexStub(stub: ScSelfTypeElementStub, sink: IndexSink): Unit = {
    sink.occurrences(ScalaIndexKeys.SELF_TYPE_CLASS_NAME_KEY, stub.classNames.toSeq: _*)
  }

  override def createPsi(stub: ScSelfTypeElementStub): ScSelfTypeElement = new ScSelfTypeElementImpl(stub)

  // Preserves the former `getLanguage.getDisplayName.toLowerCase + "." + debugName` external id.
  override def getExternalId: String = s"scala.${ScSelfTypeElementElementType.DebugName}"

  override def shouldCreateStub(node: ASTNode): Boolean = !ScStubElementType.isLocal(node)
}
