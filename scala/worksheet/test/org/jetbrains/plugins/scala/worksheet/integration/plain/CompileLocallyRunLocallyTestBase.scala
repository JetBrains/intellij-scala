package org.jetbrains.plugins.scala.worksheet.integration.plain

abstract class CompileLocallyRunLocallyTestBase extends PlainWorksheetTestBase {

  override def useCompileServer: Boolean = false

  // the value doesn't actually matter, cause compile server isn't used anyway
  override def runInCompileServerProcess = false
}
