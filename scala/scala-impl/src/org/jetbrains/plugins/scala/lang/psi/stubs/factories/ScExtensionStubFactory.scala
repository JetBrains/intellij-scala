package org.jetbrains.plugins.scala.lang.psi.stubs.factories

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScExtension
import org.jetbrains.plugins.scala.lang.psi.impl.statements.ScExtensionImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScExtensionElementType
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScExtensionStubImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ExtensionIndex
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys.TOP_LEVEL_EXTENSION_BY_PKG_KEY
import org.jetbrains.plugins.scala.lang.psi.stubs.{ScExtensionStub, ScImplicitStub}

final class ScExtensionStubFactory(elementType: ScExtensionElementType) extends ScStubSerializingElementFactory[ScExtensionStub, ScExtension](elementType) {
  override def serialize(stub: ScExtensionStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeBoolean(stub.isTopLevel)
    dataStream.writeOptionName(stub.topLevelQualifier)
    dataStream.writeOptionName(stub.extensionTargetClass)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScExtensionStub =
    new ScExtensionStubImpl(
      parent = parentStub,
      elementType = elementType,
      isTopLevel = dataStream.readBoolean(),
      topLevelQualifier = dataStream.readOptionName,
      extensionTargetClass = dataStream.readOptionName
    )

  override def createStubImpl(extension: ScExtension, parentStub: StubElement[_ <: PsiElement]): ScExtensionStub =
    new ScExtensionStubImpl(
      parent = parentStub,
      elementType = elementType,
      isTopLevel = extension.isTopLevel,
      topLevelQualifier = extension.topLevelQualifier,
      extensionTargetClass = ScImplicitStub.conversionParamClass(extension)
    )

  override def createPsi(stub: ScExtensionStub): ScExtension = new ScExtensionImpl(stub, null)

  override def indexStub(stub: ScExtensionStub, sink: IndexSink): Unit = {
    if (stub.isTopLevel) {
      stub.topLevelQualifier.foreach { x =>
        sink.occurrence(TOP_LEVEL_EXTENSION_BY_PKG_KEY, x)
      }
    }

    stub.extensionTargetClass.foreach(ExtensionIndex.occurrence(sink, _))
  }
}
