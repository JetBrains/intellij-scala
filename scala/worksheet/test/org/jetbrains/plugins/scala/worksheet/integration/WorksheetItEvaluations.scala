package org.jetbrains.plugins.scala.worksheet.integration

import com.intellij.openapi.editor.Editor
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.ui.UIUtil
import javax.swing.SwingUtilities
import org.jetbrains.plugins.scala.worksheet.actions.topmenu.RunWorksheetAction
import org.jetbrains.plugins.scala.worksheet.integration.WorksheetIntegrationBaseTest.TestRunResult
import org.junit.Assert.fail

import scala.concurrent.Future
import scala.concurrent.duration.{Duration, DurationInt}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

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
    val result = WorksheetItEvaluations.await(future, evaluationTimeout)
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    result match {
      case Some(Success(result))    => result
      case Some(Failure(exception)) => throw exception
      case None                     => fail("Timeout period was exceeded while waiting for worksheet evaluation").asInstanceOf[Nothing]
    }
  }
}

object WorksheetItEvaluations {

  private def await[T](future: Future[T],
                         duration: Duration,
                         sleepInterval: Duration = 100 milliseconds): Option[Try[T]] = {
    var timeSpent: Duration = Duration.Zero
    while (!future.isCompleted && (timeSpent < duration)) {
      if (SwingUtilities.isEventDispatchThread) {
        UIUtil.dispatchAllInvocationEvents()
      }
      Thread.sleep(sleepInterval.toMillis)
      timeSpent = timeSpent.plus(sleepInterval)
    }

    future.value
  }
}
