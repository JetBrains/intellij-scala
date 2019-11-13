package org.jetbrains.plugins.scala.worksheet.integration

import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.ui.UIUtil
import javax.swing.SwingUtilities
import org.jetbrains.plugins.scala.extensions.StringExt
import org.jetbrains.plugins.scala.worksheet.actions.topmenu.RunWorksheetAction
import org.jetbrains.plugins.scala.worksheet.integration.WorksheetIntegrationBaseTest.TestRunResult
import org.junit.Assert.fail

import scala.concurrent.Future
import scala.concurrent.duration.{Duration, DurationInt}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

trait WorksheetItEvaluations {
  self: WorksheetIntegrationBaseTest =>

  protected def runWorksheetEvaluation(text: String): TestRunResult = {
    val worksheetEditor = prepareWorksheetEditor(text.withNormalizedSeparator)
    // NOTE: worksheet backend / frontend initialization is done under the hood on calling action
    val future = RunWorksheetAction.runCompiler(project, worksheetEditor, auto = false)
    val evaluationResult = waitForEvaluationEnd(future)
    TestRunResult(worksheetEditor, evaluationResult)
  }

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
