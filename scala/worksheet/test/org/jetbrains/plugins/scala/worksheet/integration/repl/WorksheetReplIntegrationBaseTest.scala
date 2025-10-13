package org.jetbrains.plugins.scala.worksheet.integration.repl

import org.jetbrains.plugins.scala.util.runners.MultipleScalaVersionsJUnit4Runner
import org.jetbrains.plugins.scala.worksheet.integration.WorksheetIntegrationBaseTest
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetExternalRunType
import org.junit.runner.RunWith

import scala.language.postfixOps

@RunWith(classOf[MultipleScalaVersionsJUnit4Runner])
abstract class WorksheetReplIntegrationBaseTest extends WorksheetIntegrationBaseTest {

  override def useCompileServer: Boolean = true

  override def runInCompileServerProcess: Boolean = true

  override def runType: WorksheetExternalRunType = WorksheetExternalRunType.ReplRunType
}
