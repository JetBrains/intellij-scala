package org.jetbrains.plugins.scala.worksheet.integration

import com.intellij.compiler.CompilerMessageImpl
import com.intellij.openapi.editor.{Editor, FoldRegion}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.compiler.CompileServerLauncher
import org.jetbrains.plugins.scala.debugger.ScalaCompilerTestBase
import org.jetbrains.plugins.scala.extensions.{StringExt, TextRangeExt}
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.util.MarkersUtils
import org.jetbrains.plugins.scala.worksheet.actions.topmenu.RunWorksheetAction.RunWorksheetActionResult
import org.jetbrains.plugins.scala.worksheet.integration.WorksheetIntegrationBaseTest._
import org.jetbrains.plugins.scala.worksheet.runconfiguration.WorksheetCache
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetCommonSettings
import org.jetbrains.plugins.scala.{ScalaVersion, Scala_2_10, Scala_2_11, Scala_2_12, Scala_2_13, SlowTests}
import org.junit.Assert._
import org.junit.experimental.categories.Category

import scala.concurrent.duration.{Duration, DurationInt}
import scala.language.postfixOps

/*
  TODO 1: move to a separate category and run in TeamCity multiple times against different scala versions
  TODO 2: check that run / stop buttons are enabled/disabled when evaluation is in process/ended
  TODO 3: test clean action
  TODO 4: test Repl iterative evaluation
  TODO 5: test split SimpleWorksheetSplitter polygons coordinates in different scrolling positions
*/
@Category(Array(classOf[SlowTests]))
abstract class WorksheetIntegrationBaseTest extends ScalaCompilerTestBase
  with WorksheetItEditorPreparations
  with WorksheetItEvaluations
  with WorksheetItAssertions {
  self: WorksheetRunTestSettings =>

  protected val (foldStart, foldEnd) = ("<folding>", "</folding>")
  protected val (foldStartExpanded, foldEndExpanded) = ("<foldingExpanded>", "</foldingExpanded>")

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= Scala_2_10

  protected def evaluationTimeout: Duration = 60 seconds

  override protected def useCompileServer: Boolean = self.compileInCompileServerProcess

  protected implicit def project: Project = getProject

  protected def worksheetCache = WorksheetCache.getInstance(project)

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

  protected def doRenderTest(before: String, afterWithFoldings: String): Editor = {
    val beforeFixed = before
    val (afterFixed, foldings) = preprocessViewerText(afterWithFoldings)
    doRenderTest(beforeFixed, afterFixed, foldings)
  }

  private def doRenderTest(
    before: String,
    after: String,
    foldings: Seq[Folding]
  ): Editor = {
    val TestRunResult(editor, evaluationResult) = doRenderTestWithoutCompilationChecks(before, after, foldings)

    assertEquals(RunWorksheetActionResult.Done, evaluationResult)

    assertCompiledWithoutErrors(editor)
    assertCompiledWithoutWarnings(editor)

    editor
  }

  protected def doRenderTestWithoutCompilationChecks(before: String, afterWithFoldings: String): TestRunResult = {
    val beforeFixed = before
    val (afterFixed, foldings) = preprocessViewerText(afterWithFoldings)
    doRenderTestWithoutCompilationChecks(beforeFixed, afterFixed, foldings)
  }

  private def doRenderTestWithoutCompilationChecks(
    before: String,
    after: String,
    foldings: Seq[Folding]
  ): TestRunResult = {
    val result = runWorksheetEvaluation(before)
    val ViewerEditorData(_, actualText, actualFoldings) = viewerEditorData(result.worksheetEditor)

    assertEquals(after, actualText)

    assertFoldings(foldings, actualFoldings)

    result
  }

  protected def doFailingTest(text: String, expectedError: RunWorksheetActionResult.Error): Editor =
    doResultTest(text, expectedError)

  protected def doResultTest(text: String, expectedError: RunWorksheetActionResult): Editor = {
    val TestRunResult(editor, actualResult) = runWorksheetEvaluation(text)
    assertEquals(expectedError, actualResult)
    editor
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

  protected def collectedCompilerMessages(editor: Editor): Seq[CompilerMessageImpl] = {
    val collector = worksheetCache.getCompilerMessagesCollector(editor).orNull
    assertNotNull(collector)
    collector.collectedMessages.map(_.asInstanceOf[CompilerMessageImpl])
  }

  protected def viewerEditorData(worksheetEditor: Editor): ViewerEditorData =
    viewerEditorDataOpt(worksheetEditor)
      .getOrElse(fail("Viewer editor is empty").asInstanceOf[Nothing])

  protected def viewerEditorDataOpt(worksheetEditor: Editor): Option[ViewerEditorData] =
    Option(worksheetCache.getViewer(worksheetEditor)).map { editor =>
      val renderedText = editor.getDocument.getText
      val foldings = editor.getFoldingModel.getAllFoldRegions.map(Folding.apply)
      ViewerEditorData(editor, renderedText, foldings)
    }

  protected def assertNoViewerEditorOutput(worksheetEditor: Editor): Unit =
    viewerEditorDataOpt(worksheetEditor) match {
      case Some(ViewerEditorData(_, text, foldings)) =>
        fail(
          s"""no output is expected in viewer editor, but got:
            |text: $text
            |foldings: $foldings""".stripMargin
        )
      case None =>
    }
}

object WorksheetIntegrationBaseTest {

  case class TestRunResult(
    worksheetEditor: Editor,
    runResult: RunWorksheetActionResult
  )

  case class ViewerEditorData(
    editor: Editor,
    text: String,
    foldings: Seq[Folding]
  )

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
}
