package org.jetbrains.plugins.scala.worksheet.integration.plain

import org.jetbrains.plugins.scala.worksheet.integration.WorksheetIntegrationBaseTest
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetExternalRunType

abstract class PlainWorksheetTestBase extends WorksheetIntegrationBaseTest {
  override def runType: WorksheetExternalRunType = WorksheetExternalRunType.PlainRunType
}
