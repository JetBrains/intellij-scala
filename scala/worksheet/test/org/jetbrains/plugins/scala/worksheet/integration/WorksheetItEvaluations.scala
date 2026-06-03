package org.jetbrains.plugins.scala.worksheet.integration

import com.intellij.testFramework.PlatformTestUtil
import com.intellij.ui.content.MessageView
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.scala.worksheet.actions.topmenu.RunWorksheetAction
import org.jetbrains.plugins.scala.worksheet.integration.WorksheetIntegrationBaseTest.{TestRunResult, WorksheetEditorAndFile}
import org.junit.Assert.fail

import javax.swing.SwingUtilities
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

  protected def runWorksheetEvaluationAndWait(worksheetEditor: WorksheetEditorAndFile): TestRunResult = {
    val future = runWorksheetEvaluation(worksheetEditor)
    TestRunResult(worksheetEditor, waitForEvaluationEnd(future))
  }

  protected def runWorksheetEvaluation(worksheetEditor: WorksheetEditorAndFile): Future[RunWorksheetAction.RunWorksheetActionResult] = {
    //HACK: force service to initialize, otherwise NPE can occur in WorksheetCompilerUtil.removeOldMessageContent
    //because `MessageView.getInstance` uses invokeLater under the hood and toolwindow is not initialized
    MessageView.getInstance(getProject)
    UIUtil.dispatchAllInvocationEvents()
    RunWorksheetAction.runCompiler(worksheetEditor.editor, worksheetEditor.psiFile, auto = false)
  }

  protected def waitForEvaluationEnd(future: Future[RunWorksheetAction.RunWorksheetActionResult]): RunWorksheetAction.RunWorksheetActionResult = {
    val result = awaitWithoutUiStarving(future, evaluationTimeout)
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    result match {
      case Some(Success(result))    => result
      case Some(Failure(exception)) => throw exception
      case None                     => fail("Timeout period was exceeded while waiting for worksheet evaluation").asInstanceOf[Nothing]
    }
  }


  /**
   * TODO: delete and override com.intellij.testFramework.UsefulTestCase#runInDispatchThread() instead
   * Unit tests are run in EDT, so we can't just use [[scala.concurrent.Await.result]] - it will block EDT and lead to
   * all EDT events starving. So no code in "invokeLater" or "invokeLaterAndWait" etc... will be executed.
   * We must periodically flush EDT events to workaround this.
   */
  def awaitWithoutUiStarving[T](
    future: Future[T],
    timeout: Duration,
    sleepInterval: Duration = 100.milliseconds
  ): Option[Try[T]] = {
    var timeSpent: Duration = Duration.Zero

    while (!future.isCompleted && (timeSpent < timeout)) {
      if (SwingUtilities.isEventDispatchThread) {
        UIUtil.dispatchAllInvocationEvents()
      }
      Thread.sleep(sleepInterval.toMillis)
      timeSpent = timeSpent.plus(sleepInterval)
    }

    future.value
  }
}
