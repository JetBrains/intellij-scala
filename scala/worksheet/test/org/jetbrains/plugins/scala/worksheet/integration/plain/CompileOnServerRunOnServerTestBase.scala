package org.jetbrains.plugins.scala.worksheet.integration.plain

abstract class CompileOnServerRunOnServerTestBase extends PlainWorksheetTestBase {

  override def useCompileServer: Boolean = true

  override def runInCompileServerProcess: Boolean = true
}
