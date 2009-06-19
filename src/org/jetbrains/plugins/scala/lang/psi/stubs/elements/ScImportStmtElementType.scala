package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import api.base.ScModifierList
import api.toplevel.imports.ScImportStmt
import impl.{ScImportStmtStubImpl, ScTemplateParentsStubImpl}
import api.toplevel.templates.ScTemplateParents
import com.intellij.util.io.StringRef
import com.intellij.psi.stubs.{StubElement, IndexSink, StubOutputStream, StubInputStream}
import com.intellij.psi.PsiElement
import psi.impl.toplevel.imports.ScImportStmtImpl
/**
 * User: Alexander Podkhalyuzin
 * Date: 18.06.2009
 */

class ScImportStmtElementType[Func <: ScImportStmt]
        extends ScStubElementType[ScImportStmtStub, ScImportStmt]("import statement") {
  def serialize(stub: ScImportStmtStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeName(stub.getImportText)
  }

  def createStubImpl[ParentPsi <: PsiElement](psi: ScImportStmt, parentStub: StubElement[ParentPsi]): ScImportStmtStub = {
    new ScImportStmtStubImpl(parentStub, this, psi.getText)
  }

  def deserializeImpl(dataStream: StubInputStream, parentStub: Any): ScImportStmtStub = {
    val text = dataStream.readName.toString
    new ScImportStmtStubImpl(parentStub.asInstanceOf[StubElement[PsiElement]], this, text)
  }

  def indexStub(stub: ScImportStmtStub, sink: IndexSink): Unit = {}

  def createPsi(stub: ScImportStmtStub): ScImportStmt = {
    new ScImportStmtImpl(stub)
  }
}