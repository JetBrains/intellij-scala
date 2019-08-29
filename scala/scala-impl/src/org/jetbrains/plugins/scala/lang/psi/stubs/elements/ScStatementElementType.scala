package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, StubInputStream, StubOutputStream}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.imports.{ScExportStmtImpl, ScImportStmtImpl}
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScImportStmtStubImpl

/**
 * User: Alexander Podkhalyuzin
 * Date: 18.06.2009
 */
sealed abstract class ScStatementElementType(debugName: String)
  extends ScStubElementType[ScImportStmtStub, ScImportStmt](debugName) {

  override def serialize(stub: ScImportStmtStub, dataStream: StubOutputStream): Unit = {
    dataStream.writeName(stub.importText)
  }

  override def deserialize(dataStream: StubInputStream,
                           parentStub: StubElement[_ <: PsiElement]) = new ScImportStmtStubImpl(
    parentStub,
    this,
    importText = dataStream.readNameString
  )

  override def createStubImpl(statement: ScImportStmt,
                              parentStub: StubElement[_ <: PsiElement]) = new ScImportStmtStubImpl(
    parentStub,
    this,
    importText = statement.getText
  )
}

object ImportStatement extends ScStatementElementType("import statement") {

  override def createElement(node: ASTNode) = new ScImportStmtImpl(null, null, node)

  override def createPsi(stub: ScImportStmtStub) = new ScImportStmtImpl(stub, this, null)
}

object ExportStatement extends ScStatementElementType("export statement") {

  override def createElement(node: ASTNode) = new ScExportStmtImpl(null, null, node)

  override def createPsi(stub: ScImportStmtStub) = new ScExportStmtImpl(stub, this, null)
}