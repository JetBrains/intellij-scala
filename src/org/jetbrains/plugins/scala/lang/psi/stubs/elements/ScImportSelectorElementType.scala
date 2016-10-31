package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, StubInputStream, StubOutputStream}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportSelector
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.imports.ScImportSelectorImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScImportSelectorStubImpl

/**
  * User: Alexander Podkhalyuzin
  * Date: 20.06.2009
  */
class ScImportSelectorElementType extends ScStubElementType[ScImportSelectorStub, ScImportSelector]("import selector") {
  override def serialize(stub: ScImportSelectorStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeOptionName(stub.referenceText)
    dataStream.writeOptionName(stub.importedName)
    dataStream.writeBoolean(stub.isAliasedImport)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScImportSelectorStub =
    new ScImportSelectorStubImpl(parentStub, this,
      referenceTextRef = dataStream.readOptionName,
      importedNameRef = dataStream.readOptionName,
      isAliasedImport = dataStream.readBoolean)

  override def createStub(selector: ScImportSelector, parentStub: StubElement[_ <: PsiElement]): ScImportSelectorStub = {
    val referenceText = selector.reference.map {
      _.getText
    }

    new ScImportSelectorStubImpl(parentStub, this,
      referenceTextRef = referenceText.asReference,
      importedNameRef = selector.importedName.asReference,
      isAliasedImport = selector.isAliasedImport)
  }

  override def createElement(node: ASTNode): ScImportSelector = new ScImportSelectorImpl(node)

  override def createPsi(stub: ScImportSelectorStub): ScImportSelector = new ScImportSelectorImpl(stub)
}