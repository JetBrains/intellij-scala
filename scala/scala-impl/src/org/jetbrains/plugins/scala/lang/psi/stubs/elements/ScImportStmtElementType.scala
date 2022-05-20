package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, StubInputStream, StubOutputStream}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScExportStmt, ScImportOrExportStmt, ScImportStmt}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.imports.{ScExportStmtImpl, ScImportStmtImpl}
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.{ScExportStmtStubImpl, ScImportStmtStubImpl}
import org.jetbrains.plugins.scala.lang.psi.stubs.{ScExportStmtStub, ScImportOrExportStmtStub, ScImportStmtStub}

abstract sealed class ScImportOrExportStmtElementType[
  P <: ScImportOrExportStmt,
  S >: Null <: ScImportOrExportStmtStub[P],
](
  debugName: String
) extends ScStubElementType.Impl[S, P](debugName)

class ScImportStmtElementType extends ScImportOrExportStmtElementType[ScImportStmt, ScImportStmtStub]("ScImportStatement") {

  override protected def createPsi(stub: ScImportStmtStub, nodeType: this.type, node: ASTNode, debugName: String) =
    new ScImportStmtImpl(stub, nodeType, node, debugName)

  override final def createStubImpl(statement: ScImportStmt, parentStub: StubElement[_ <: PsiElement]) =
    new ScImportStmtStubImpl(parentStub, this, importText = statement.getText)

  override final def serialize(stub: ScImportStmtStub, dataStream: StubOutputStream): Unit =
    dataStream.writeName(stub.importText)

  override final def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]) =
    new ScImportStmtStubImpl(parentStub, this, importText = dataStream.readNameString)
}

class ScExportStmtElementType extends ScImportOrExportStmtElementType[ScExportStmt, ScExportStmtStub]("ScExportStatement") {

  override protected def createPsi(stub: ScExportStmtStub, nodeType: this.type, node: ASTNode, debugName: String) =
    new ScExportStmtImpl(stub, nodeType, node, debugName)

  override final def createStubImpl(statement: ScExportStmt, parentStub: StubElement[_ <: PsiElement]) =
    new ScExportStmtStubImpl(parentStub, this, importText = statement.getText)

  override final def serialize(stub: ScExportStmtStub, dataStream: StubOutputStream): Unit =
    dataStream.writeName(stub.importText)

  override final def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]) =
    new ScExportStmtStubImpl(parentStub, this, importText = dataStream.readNameString)
}