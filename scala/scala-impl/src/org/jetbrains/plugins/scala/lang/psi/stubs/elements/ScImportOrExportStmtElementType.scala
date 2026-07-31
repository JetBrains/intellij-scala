package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScExportStmt, ScImportOrExportStmt, ScImportStmt}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.imports.{ScExportStmtImpl, ScImportStmtImpl}

abstract sealed class ScImportOrExportStmtElementType[P <: ScImportOrExportStmt](debugName: String)
  extends ScStubElementType[P](debugName)

final class ScImportStmtElementType extends ScImportOrExportStmtElementType[ScImportStmt]("ScImportStatement") {
  override def createElement(node: ASTNode): ScImportStmt = new ScImportStmtImpl(null, null, node, toString)
}

final class ScExportStmtElementType extends ScImportOrExportStmtElementType[ScExportStmt]("ScExportStatement") {
  override def createElement(node: ASTNode): ScExportStmt = new ScExportStmtImpl(null, null, node, toString)
}
