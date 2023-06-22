package org.jetbrains.plugins.scala.lang.psi.stubs

import com.intellij.psi.stubs.StubElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScExportStmt, ScImportOrExportStmt, ScImportStmt}

trait ScImportOrExportStmtStub[Psi <: ScImportOrExportStmt] extends StubElement[Psi] {
  def importText: String
}

trait ScImportStmtStub extends ScImportOrExportStmtStub[ScImportStmt]
trait ScExportStmtStub extends ScImportOrExportStmtStub[ScExportStmt] with ScTopLevelElementStub[ScExportStmt]