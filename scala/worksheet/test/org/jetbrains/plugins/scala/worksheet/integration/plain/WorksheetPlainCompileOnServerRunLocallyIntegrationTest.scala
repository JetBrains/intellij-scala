package org.jetbrains.plugins.scala.worksheet.integration.plain

class WorksheetPlainCompileOnServerRunLocallyIntegrationTest extends WorksheetPlainIntegrationBaseTest {

  override def useCompileServer = true

  override def runInCompileServerProcess = false
}
