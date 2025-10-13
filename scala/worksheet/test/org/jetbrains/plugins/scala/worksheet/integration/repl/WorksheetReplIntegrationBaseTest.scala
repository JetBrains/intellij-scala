package org.jetbrains.plugins.scala.worksheet.integration.repl

import org.jetbrains.plugins.scala.worksheet.integration.WorksheetIntegrationBaseTest
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetExternalRunType

import scala.language.postfixOps

abstract class WorksheetReplIntegrationBaseTest extends WorksheetIntegrationBaseTest {

  override def useCompileServer: Boolean = true

  override def runInCompileServerProcess: Boolean = true

  override def runType: WorksheetExternalRunType = WorksheetExternalRunType.ReplRunType
}
