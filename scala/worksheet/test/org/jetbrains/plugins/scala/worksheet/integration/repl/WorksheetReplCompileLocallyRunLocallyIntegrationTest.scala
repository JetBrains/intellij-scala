package org.jetbrains.plugins.scala.worksheet.integration.repl

import org.jetbrains.plugins.scala.SlowTests
import org.jetbrains.plugins.scala.worksheet.actions.topmenu.RunWorksheetAction.RunWorksheetActionResult
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompiler.WorksheetCompilerResult.PreconditionError
import org.junit.experimental.categories.Category

import scala.language.postfixOps

@Category(Array(classOf[SlowTests]))
class WorksheetReplCompileLocallyRunLocallyIntegrationTest extends WorksheetReplIntegrationBaseTest {

  override def compileInCompileServerProcess: Boolean = false

  override def runInCompileServerProcess: Boolean = false

  def testSimpleDeclaration(): Unit = {
    val left = "val a = 1"
    val compilerError = PreconditionError("Worksheet can be executed in REPL mode only in compile server process")
    doFailingTest(left, RunWorksheetActionResult.WorksheetCompilerError(compilerError))
  }
}
