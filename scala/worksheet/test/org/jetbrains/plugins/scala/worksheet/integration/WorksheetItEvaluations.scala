package org.jetbrains.plugins.scala.worksheet.integration

import com.intellij.openapi.editor.Editor
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

    val evaluationResult = evaluateWorksheetAndWait(worksheetEditor) match {
      case Some(Success(result))    => result
      case Some(Failure(exception)) => throw exception
      case None                     => fail("Timeout period was exceeded while waiting for worksheet evaluation").asInstanceOf[Nothing]
    }

    TestRunResult( worksheetEditor, evaluationResult)
  }

  private def evaluateWorksheetAndWait(worksheetEditor: Editor): Option[Try[RunWorksheetAction.RunWorksheetActionResult]] = {
    // NOTE: worksheet backend / frontend initialization is done under the hood on calling action
    val future = RunWorksheetAction.runCompiler(project, worksheetEditor, auto = false)
    val result = WorksheetItEvaluations.await(future, 60 seconds)
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    result
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
