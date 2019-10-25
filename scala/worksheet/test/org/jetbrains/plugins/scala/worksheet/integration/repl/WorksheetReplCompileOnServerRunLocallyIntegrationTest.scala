package org.jetbrains.plugins.scala.worksheet.integration.repl

import org.jetbrains.plugins.scala.SlowTests
import org.jetbrains.plugins.scala.worksheet.actions.topmenu.RunWorksheetAction.RunWorksheetActionResult
import org.jetbrains.plugins.scala.worksheet.integration.{WorksheetIntegrationBaseTest, WorksheetRunTestSettings}
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompiler.WorksheetCompilerResult.PreconditionError
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetExternalRunType
import org.junit.experimental.categories.Category

import scala.language.postfixOps

@Category(Array(classOf[SlowTests]))
class WorksheetReplCompileOnServerRunLocallyIntegrationTest extends WorksheetIntegrationBaseTest with WorksheetRunTestSettings {

  override def runType: WorksheetExternalRunType = WorksheetExternalRunType.ReplRunType

  override def compileInCompileServerProcess: Boolean = true

  override def runInCompileServerProcess: Boolean = false

  def testSimpleDeclaration(): Unit = {
    val left = "val a = 1"
    val compilerError = PreconditionError("Worksheet can be executed in REPL mode only in compile server process")
    doFailingTest(left, RunWorksheetActionResult.WorksheetCompilerError(compilerError))
  }
}
