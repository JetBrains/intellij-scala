package org.jetbrains.plugins.scala.worksheet.integration.repl

import org.jetbrains.plugins.scala.util.runners.{RunWithScalaVersions, TestScalaVersion}
import org.jetbrains.plugins.scala.worksheet.actions.topmenu.RunWorksheetAction.RunWorksheetActionResult
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompiler.WorksheetCompilerResult.{Precondition, PreconditionError}

import scala.language.postfixOps

@RunWithScalaVersions(Array(TestScalaVersion.Scala_2_13))
class WorksheetReplCompileLocallyRunLocallyIntegrationTest extends WorksheetReplIntegrationBaseTest {

  override def useCompileServer: Boolean = false

  override def runInCompileServerProcess: Boolean = false

  def testSimpleDeclaration(): Unit = {
    val left = "val a = 1"
    val compilerError = PreconditionError(Precondition.ReplRequiresCompileServerProcess)
    doFailingTest(left, RunWorksheetActionResult.WorksheetRunError(compilerError))
  }
}
