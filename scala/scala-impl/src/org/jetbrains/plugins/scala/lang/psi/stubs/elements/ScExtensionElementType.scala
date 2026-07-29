package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream, StubSerializingElementFactory}
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScExtension
import org.jetbrains.plugins.scala.lang.psi.impl.statements.ScExtensionImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScExtensionStubImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ExtensionIndex
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys._
import org.jetbrains.plugins.scala.lang.psi.stubs.{ScExtensionStub, ScImplicitStub}

class ScExtensionElementType extends ScalaStubBasedElementType[ScExtensionStub, ScExtension](ScExtensionElementType.DebugName) {
  override def createElement(node: ASTNode): ScExtension = new ScExtensionImpl(null, node)
}

object ScExtensionElementType {
  val DebugName = "extension"
}

class ScExtensionStubFactory(elementType: IElementType) extends StubSerializingElementFactory[ScExtensionStub, ScExtension] {

  override def serialize(stub: ScExtensionStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeBoolean(stub.isTopLevel)
    dataStream.writeOptionName(stub.topLevelQualifier)
    dataStream.writeOptionName(stub.extensionTargetClass)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScExtensionStub =
    new ScExtensionStubImpl(
      parent               = parentStub,
      elementType          = elementType,
      isTopLevel           = dataStream.readBoolean(),
      topLevelQualifier    = dataStream.readOptionName,
      extensionTargetClass = dataStream.readOptionName
    )

  override def createStub(extension: ScExtension, parentStub: StubElement[_ <: PsiElement]): ScExtensionStub =
    ScStubElementType.Processing.run {
      new ScExtensionStubImpl(
        parent               = parentStub,
        elementType          = elementType,
        isTopLevel           = extension.isTopLevel,
        topLevelQualifier    = extension.topLevelQualifier,
        extensionTargetClass = ScImplicitStub.conversionParamClass(extension)
      )
    }

  override def createPsi(stub: ScExtensionStub): ScExtension = new ScExtensionImpl(stub, null)

  override def indexStub(stub: ScExtensionStub, sink: IndexSink): Unit = {
    if (stub.isTopLevel) {
      stub.topLevelQualifier.foreach { x =>
        sink.occurrence(TOP_LEVEL_EXTENSION_BY_PKG_KEY, x)
      }
    }

    stub.extensionTargetClass.foreach(ExtensionIndex.occurrence(sink, _))
  }

  override def getExternalId: String = s"scala.${ScExtensionElementType.DebugName}"

  override def shouldCreateStub(node: ASTNode): Boolean = !ScStubElementType.isLocal(node)
}
