package org.jetbrains.plugins.scala.worksheet.integration.plain

class WorksheetPlainCompileOnServerRunOnServerIntegrationTest extends WorksheetPlainIntegrationBaseTest {

  override def useCompileServer = true

  override def runInCompileServerProcess = true
}
