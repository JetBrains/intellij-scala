package org.jetbrains.plugins.scala.worksheet.integration.plain

abstract class CompileOnServerRunLocallyTestBase extends PlainWorksheetTestBase {

  override def useCompileServer: Boolean = true

  override def runInCompileServerProcess: Boolean = false
}
