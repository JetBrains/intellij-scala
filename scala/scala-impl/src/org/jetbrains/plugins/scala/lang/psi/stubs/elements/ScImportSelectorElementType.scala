package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream, StubSerializingElementFactory}
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportSelector
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.imports.ScImportSelectorImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScImportSelectorStub
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScImportSelectorStubImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys

class ScImportSelectorElementType extends ScalaStubBasedElementType[ScImportSelectorStub, ScImportSelector](ScImportSelectorElementType.DebugName) {
  override def createElement(node: ASTNode): ScImportSelector = new ScImportSelectorImpl(node)
}

object ScImportSelectorElementType {
  val DebugName = "import selector"
}

class ScImportSelectorStubFactory(elementType: IElementType)
  extends StubSerializingElementFactory[ScImportSelectorStub, ScImportSelector] {

  override def serialize(stub: ScImportSelectorStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeOptionName(stub.referenceText)
    dataStream.writeOptionName(stub.importedName)
    dataStream.writeOptionName(stub.aliasName)
    dataStream.writeBoolean(stub.isAliasedImport)
    dataStream.writeBoolean(stub.isWildcardSelector)
    dataStream.writeBoolean(stub.isGivenSelector)
    dataStream.writeOptionName(stub.typeText)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScImportSelectorStub =
    new ScImportSelectorStubImpl(parentStub, elementType,
      referenceText = dataStream.readOptionName,
      importedName = dataStream.readOptionName,
      aliasName = dataStream.readOptionName,
      isAliasedImport = dataStream.readBoolean(),
      isWildcardSelector = dataStream.readBoolean(),
      isGivenSelector = dataStream.readBoolean(),
      typeText = dataStream.readOptionName
    )

  override def createStub(selector: ScImportSelector, parentStub: StubElement[_ <: PsiElement]): ScImportSelectorStub =
    ScStubElementType.Processing.run {
      new ScImportSelectorStubImpl(parentStub, elementType,
        referenceText = selector.reference.map(_.getText),
        importedName = selector.importedName,
        aliasName = selector.aliasName,
        isAliasedImport = selector.isAliasedImport,
        isWildcardSelector = selector.isWildcardSelector,
        isGivenSelector = selector.isGivenSelector,
        typeText = selector.givenTypeElement.map(_.getText)
      )
    }

  override def createPsi(stub: ScImportSelectorStub): ScImportSelector = new ScImportSelectorImpl(stub)

  override def indexStub(stub: ScImportSelectorStub, sink: IndexSink): Unit =
    stub.referenceText.foreach {
      sink.occurrence(ScalaIndexKeys.ALIASED_IMPORT_KEY, _)
    }

  override def getExternalId: String = s"scala.${ScImportSelectorElementType.DebugName}"

  override def shouldCreateStub(node: ASTNode): Boolean = !ScStubElementType.isLocal(node)
}
