package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, StubInputStream, StubOutputStream}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportSelector
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.imports.ScImportSelectorImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScImportSelectorStub
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScImportSelectorStubImpl

class ScImportSelectorElementType extends ScStubElementType[ScImportSelectorStub, ScImportSelector]("import selector") {
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
    new ScImportSelectorStubImpl(parentStub, this,
      referenceText = dataStream.readOptionName,
      importedName = dataStream.readOptionName,
      aliasName = dataStream.readOptionName,
      isAliasedImport = dataStream.readBoolean(),
      isWildcardSelector = dataStream.readBoolean(),
      isGivenSelector = dataStream.readBoolean(),
      typeText = dataStream.readOptionName
    )

  override def createStubImpl(selector: ScImportSelector, parentStub: StubElement[_ <: PsiElement]): ScImportSelectorStub = {
    val referenceText = selector.reference.map {
      _.getText
    }

    new ScImportSelectorStubImpl(parentStub, this,
      referenceText = referenceText,
      importedName = selector.importedName,
      aliasName = selector.aliasName,
      isAliasedImport = selector.isAliasedImport,
      isWildcardSelector = selector.isWildcardSelector,
      isGivenSelector = selector.isGivenSelector,
      typeText = selector.givenTypeElement.map(_.getText)
    )
  }

  override def createElement(node: ASTNode): ScImportSelector = new ScImportSelectorImpl(node)

  override def createPsi(stub: ScImportSelectorStub): ScImportSelector = new ScImportSelectorImpl(stub)
}