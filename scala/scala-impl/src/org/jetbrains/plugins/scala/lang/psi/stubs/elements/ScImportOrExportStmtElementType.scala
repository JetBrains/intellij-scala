package org.jetbrains.plugins.scala.lang.psi.stubs.elements

abstract sealed class ScImportOrExportStmtElementType(debugName: String)
  extends ScStubElementType(debugName)

final class ScImportStmtElementType extends ScImportOrExportStmtElementType("ScImportStatement")

final class ScExportStmtElementType extends ScImportOrExportStmtElementType("ScExportStatement")
