package org.jetbrains.plugins.scala.worksheet.integration.repl

import org.jetbrains.plugins.scala.WorksheetEvaluationTests
import org.jetbrains.plugins.scala.worksheet.integration.{WorksheetIntegrationBaseTest, WorksheetRunTestSettings}
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetExternalRunType
import org.junit.experimental.categories.Category

import scala.language.postfixOps

@Category(Array(classOf[WorksheetEvaluationTests]))
abstract class WorksheetReplIntegrationBaseTest extends WorksheetIntegrationBaseTest with WorksheetRunTestSettings {

  override def compileInCompileServerProcess: Boolean = true

  override def runInCompileServerProcess: Boolean = true

  override def runType: WorksheetExternalRunType = WorksheetExternalRunType.ReplRunType
}
