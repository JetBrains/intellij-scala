package org.jetbrains.plugins.scala.worksheet.integration

import com.intellij.openapi.editor.{Editor, FoldRegion}
import com.intellij.openapi.fileEditor.{FileEditor, FileEditorManager, TextEditor}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiFile, PsiManager}
import com.intellij.testFramework.{EdtTestUtil, PlatformTestUtil}
import com.intellij.util.ui.UIUtil
import javax.swing.SwingUtilities
import org.jetbrains.plugins.scala.compiler.CompileServerLauncher
import org.jetbrains.plugins.scala.debugger.ScalaCompilerTestBase
import org.jetbrains.plugins.scala.extensions.{StringExt, TextRangeExt}
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.util.MarkersUtils
import org.jetbrains.plugins.scala.worksheet.actions.topmenu.RunWorksheetAction
import org.jetbrains.plugins.scala.worksheet.actions.topmenu.RunWorksheetAction.RunWorksheetActionResult
import org.jetbrains.plugins.scala.worksheet.integration.WorksheetIntegrationBaseTest._
import org.jetbrains.plugins.scala.worksheet.runconfiguration.WorksheetCache
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetCommonSettings
import org.jetbrains.plugins.scala.{ScalaVersion, Scala_2_10, Scala_2_11, Scala_2_12, Scala_2_13, SlowTests}
import org.junit.Assert._
import org.junit.experimental.categories.Category

import scala.concurrent.Future
import scala.concurrent.duration.{Duration, DurationInt}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

// TODO: move to a separate category and run in TeamCity multiple times against different scala versions
@Category(Array(classOf[SlowTests]))
abstract class WorksheetIntegrationBaseTest extends ScalaCompilerTestBase {
  self: WorksheetRunTestSettings =>

  protected val (foldStart, foldEnd) = ("<folding>", "</folding>")
  protected val (foldStartExpanded, foldEndExpanded) = ("<foldingExpanded>", "</foldingExpanded>")

  override protected def supportedIn(version: ScalaVersion): Boolean = Seq(
    Scala_2_10,
    Scala_2_11,
    Scala_2_12,
    Scala_2_13
  ).contains(version)

  protected def evaluationTimeout: Duration = 60 seconds

  override protected def useCompileServer: Boolean = self.compileInCompileServerProcess

  protected def project = getProject

  protected def worksheetFileName: String = s"worksheet_${getTestName(false)}.sc"

  protected def setupWorksheetSettings(settings: WorksheetCommonSettings): Unit  = {
    settings.setRunType(self.runType)
    settings.setInteractive(false) // TODO: test these values?
    settings.setMakeBeforeRun(false)
  }

  override def setUp(): Unit = {
    super.setUp()

    ScalaProjectSettings.getInstance(project).setInProcessMode(self.runInCompileServerProcess)

    if (useCompileServer){
      println("initializing compiler server")
      val result = CompileServerLauncher.ensureServerRunning(project)
      assertTrue("compile server is expected to be running", result)
    }
  }

  protected def doTest(before: String, after: String): (Editor, String, Array[Folding]) = {
    val beforeFixed = before.withNormalizedSeparator
    val (afterFixed, foldings) = preprocessViewerText(after)
    doTest(beforeFixed, afterFixed, foldings)
  }

  protected def doFailingTest(text: String, expectedError: RunWorksheetActionResult.Error): Unit = {
    val worksheetEditor = prepareWorksheetEditor(text)
    val result = evaluateWorksheetAndWait(worksheetEditor)
    assertEquals(Some(Success(expectedError)), result)
  }

  /*
    TODO 1: check the compiler output messages ?
      (should be empty for success runs, and non-empty for some warnings/failures)
    TODO 2: check that run / stop buttons are enabled/disabled when evaluation is in process/ended
    TODO 3: test clean action
    TODO 4: test Repl iterative evaluation
    TODO 5: test split SimpleWorksheetSplitter polygons coordinates in different scrolling positions
  */
  protected def doTest(
    before: String,
    after: String,
    foldings: Seq[Folding]
  ): (Editor, String, Array[Folding]) = {
    val result@(editor, actualText, actualFoldings) = runWorksheetEvaluation(before)

    assertEquals(after, actualText)

    assertFoldings(foldings, actualFoldings)

    result
  }

  /** @return (leftEditor, text from viewer editor, folding regions) */
  protected def runWorksheetEvaluation(text: String): (Editor, String, Array[Folding]) = {
    val worksheetEditor = prepareWorksheetEditor(text.withNormalizedSeparator)
    val evaluationResult = evaluateWorksheetAndWait(worksheetEditor)

    evaluationResult match {
      case Some(Success(_))         => // ok, continue the test
      case Some(Failure(exception)) => throw exception
      case None                     => fail("Timeout period was exceeded while waiting for worksheet evaluation")
    }

    val cache = WorksheetCache.getInstance(project)
    val viewerEditor = cache.getViewer(worksheetEditor)

    val renderedText = viewerEditor.getDocument.getText
    val foldRegions = viewerEditor.getFoldingModel.getAllFoldRegions
    val foldings = foldRegions.map(Folding.apply)
    (worksheetEditor, renderedText, foldings)
  }

  private def prepareWorksheetEditor(before: String): Editor = {
    val (vFile, psiFile) = createWorksheetFile(before)

    val settings = WorksheetCommonSettings(psiFile)
    setupWorksheetSettings(settings)

    val worksheetEditor = openEditor(vFile)
    worksheetEditor
  }

  private def createWorksheetFile(before: String): (VirtualFile, PsiFile) = {
    val fileName = worksheetFileName

    val vFile = addFileToProjectSources(fileName, before)
    assertNotNull(vFile)

    val psiFile = PsiManager.getInstance(project).findFile(vFile)
    assertNotNull(psiFile)
    (vFile, psiFile)
  }

  private def openEditor(vFile: VirtualFile): Editor = {
    val editors: Array[FileEditor] = EdtTestUtil.runInEdtAndGet { () =>
      FileEditorManager.getInstance(project).openFile(vFile, true)
    }

    editors match {
      case Array(e: Editor)     => e
      case Array(e: TextEditor) => e.getEditor
      case _                    => fail(s"couldn't fond any opened editor for file $vFile").asInstanceOf[Nothing]
    }
  }

  private def evaluateWorksheetAndWait(worksheetEditor: Editor): Option[Try[RunWorksheetAction.RunWorksheetActionResult]] = {
    // NOTE: worksheet backend / frontend initialization is done under the hood on calling action
    val future = RunWorksheetAction.runCompiler(project, worksheetEditor, auto = false)
    val result = WorksheetIntegrationBaseTest.await(future, 60 seconds)
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    result
  }

  protected def assertFoldings(expectedFoldings: Seq[Folding], actualFoldings: Seq[Folding]): Unit = {
    expectedFoldings.zipAll(actualFoldings, null, null).toList.foreach { case (expected, actual) =>
      assertNotNull(
        s"""there are to few actual foldings:
           |expected : $expected
           |expected all : $expectedFoldings
           |actual all : $actualFoldings
           |""".stripMargin,
        actual
      )
      assertNotNull(
        s"""there are some unexpected foldings:
           |actual: $actual
           |expected all : $expectedFoldings
           |actual all : $actualFoldings
           |""".stripMargin,
        expected
      )
      assertEquals(expected, actual)
    }
  }

  protected def preprocessViewerText(text: String): (String, Seq[Folding]) = {
    val (textFixed, ranges) = {
      val markers = IndexedSeq((foldStart, foldEnd), (foldStartExpanded, foldEndExpanded))
      MarkersUtils.extractSequentialMarkers(text.withNormalizedSeparator, markers)
    }
    val foldings = ranges.map { case (TextRangeExt(startOffset, endOffset), markerType) =>
      Folding(startOffset, endOffset, textFixed.substring(startOffset, endOffset), isExpanded = markerType == 1)
    }
    (textFixed, foldings)
  }
}

object WorksheetIntegrationBaseTest {

  case class Folding(
    startOffset: Int,
    endOffset: Int,
    placeholderText: String,
    isExpanded: Boolean = false
  )

  object Folding {

    def apply(region: FoldRegion): Folding =
      Folding(region.getStartOffset, region.getEndOffset, region.getPlaceholderText, region.isExpanded)
  }

  protected def await[T](future: Future[T],
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
