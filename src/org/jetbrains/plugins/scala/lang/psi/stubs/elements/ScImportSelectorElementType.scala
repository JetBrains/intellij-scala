package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements


import api.toplevel.imports.{ScImportSelector, ScImportStmt}
import com.intellij.psi.stubs.{IndexSink, StubInputStream, StubElement, StubOutputStream}

import com.intellij.psi.PsiElement
import com.intellij.util.io.StringRef
import impl.{ScImportExprStubImpl, ScImportSelectorStubImpl, ScImportStmtStubImpl}
import psi.impl.toplevel.imports.{ScImportSelectorImpl, ScImportStmtImpl}
/**
 * User: Alexander Podkhalyuzin
 * Date: 20.06.2009
 */

class ScImportSelectorElementType[Func <: ScImportSelector]
        extends ScStubElementType[ScImportSelectorStub, ScImportSelector]("import selector") {
  def serialize(stub: ScImportSelectorStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeName(stub.asInstanceOf[ScImportSelectorStubImpl[_ <: PsiElement]].referenceText.toString)
    dataStream.writeName(stub.importedName)
    dataStream.writeBoolean(stub.isAliasedImport)
  }

  def createStubImpl[ParentPsi <: PsiElement](psi: ScImportSelector, parentStub: StubElement[ParentPsi]): ScImportSelectorStub = {
    val refText = psi.reference.getText
    val importedName = psi.importedName
    val aliasImport = psi.isAliasedImport
    new ScImportSelectorStubImpl(parentStub, this, refText, importedName, aliasImport)
  }

  def deserializeImpl(dataStream: StubInputStream, parentStub: Any): ScImportSelectorStub = {
    val refText = StringRef.toString(dataStream.readName)
    val importedName = StringRef.toString(dataStream.readName)
    val aliasImport = dataStream.readBoolean()
    new ScImportSelectorStubImpl(parentStub.asInstanceOf[StubElement[PsiElement]], this, refText, importedName, aliasImport)
  }

  def indexStub(stub: ScImportSelectorStub, sink: IndexSink): Unit = {}

  def createPsi(stub: ScImportSelectorStub): ScImportSelector = {
    new ScImportSelectorImpl(stub)
  }
}