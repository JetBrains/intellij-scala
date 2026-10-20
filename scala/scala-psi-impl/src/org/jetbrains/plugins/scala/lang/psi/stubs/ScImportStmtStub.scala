package org.jetbrains.plugins.scala.lang.psi.stubs

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScExportStmt, ScImportOrExportStmt, ScImportStmt}

trait ScImportOrExportStmtStub[Psi <: ScImportOrExportStmt] extends ScStubElement[Psi] {
  def importText: String
}

trait ScImportStmtStub extends ScImportOrExportStmtStub[ScImportStmt] with ScStubElement[ScImportStmt]
trait ScExportStmtStub extends ScImportOrExportStmtStub[ScExportStmt] with ScTopLevelElementStub[ScExportStmt] with ScStubElement[ScExportStmt]
