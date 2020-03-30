package org.jetbrains.plugins.scala.worksheet.integration

import com.intellij.openapi.editor.Editor
import com.intellij.testFramework.PlatformTestUtil
import org.jetbrains.plugins.scala.util.TestUtilsScala
import org.jetbrains.plugins.scala.worksheet.actions.topmenu.RunWorksheetAction
import org.jetbrains.plugins.scala.worksheet.integration.WorksheetIntegrationBaseTest.TestRunResult
import org.junit.Assert.fail

import scala.concurrent.Future
import scala.language.postfixOps
import scala.util.{Failure, Success}

trait WorksheetItEvaluations {
  self: WorksheetIntegrationBaseTest =>

  protected def runWorksheetEvaluationAndWait(text: String): TestRunResult = {
    val worksheetEditor = prepareWorksheetEditor(text)
    runWorksheetEvaluationAndWait(worksheetEditor)
  }

  protected def runWorksheetEvaluationAndWait(worksheetEditor: Editor): TestRunResult = {
    val future = runWorksheetEvaluation(worksheetEditor)
    TestRunResult(worksheetEditor, waitForEvaluationEnd(future))
  }

  protected def runWorksheetEvaluation(worksheetEditor: Editor): Future[RunWorksheetAction.RunWorksheetActionResult] =
    RunWorksheetAction.runCompiler(project, worksheetEditor, auto = false)

  protected def waitForEvaluationEnd(future: Future[RunWorksheetAction.RunWorksheetActionResult]): RunWorksheetAction.RunWorksheetActionResult = {
    val result = TestUtilsScala.awaitWithoutUiStarving(future, evaluationTimeout)
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    result match {
      case Some(Success(result))    => result
      case Some(Failure(exception)) => throw exception
      case None                     => fail("Timeout period was exceeded while waiting for worksheet evaluation").asInstanceOf[Nothing]
    }
  }
}
