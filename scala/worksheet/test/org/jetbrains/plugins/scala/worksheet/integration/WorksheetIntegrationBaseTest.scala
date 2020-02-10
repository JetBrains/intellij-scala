package org.jetbrains.plugins.scala.worksheet.integration

import com.intellij.openapi.editor.{Editor, FoldRegion}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.plugins.scala.AssertionMatchers._
import org.jetbrains.plugins.scala.compiler.{CompileServerLauncher, ScalaCompileServerSettings}
import org.jetbrains.plugins.scala.debugger.ScalaCompilerTestBase
import org.jetbrains.plugins.scala.extensions.{StringExt, TextRangeExt}
import org.jetbrains.plugins.scala.project.settings.{ScalaCompilerConfiguration, ScalaCompilerSettingsProfile}
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.util.MarkersUtils
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithJdkVersions, RunWithScalaVersions, TestJdkVersion, TestScalaVersion}
import org.jetbrains.plugins.scala.worksheet.actions.topmenu.RunWorksheetAction.RunWorksheetActionResult
import org.jetbrains.plugins.scala.worksheet.integration.WorksheetIntegrationBaseTest._
import org.jetbrains.plugins.scala.worksheet.runconfiguration.WorksheetCache
import org.jetbrains.plugins.scala.worksheet.settings.{WorksheetCommonSettings, WorksheetFileSettings}
import org.jetbrains.plugins.scala.{ScalaVersion, Scala_2_9, WorksheetEvaluationTests}
import org.junit.Assert._
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith

import scala.concurrent.duration.{Duration, DurationInt}
import scala.language.postfixOps

/*
  TODO 1: currently all tests with all configs run very long, profile and think where can we optimize them
  TODO 2: check that run / stop buttons are enabled/disabled when evaluation is in process/ended
  TODO 3: test clean action
  TODO 4: test Repl iterative evaluation
  TODO 5: test split SimpleWorksheetSplitter polygons coordinates in different scrolling positions
*/
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_10,
  TestScalaVersion.Scala_2_11,
  TestScalaVersion.Scala_2_12,
  TestScalaVersion.Scala_2_13,
))
@RunWithJdkVersions(Array(
  TestJdkVersion.JDK_1_8,
  TestJdkVersion.JDK_11
))
@RunWith(classOf[MultipleScalaVersionsRunner])
@Category(Array(classOf[WorksheetEvaluationTests]))
abstract class WorksheetIntegrationBaseTest
  extends ScalaCompilerTestBase
    with WorksheetItEditorPreparations
    with WorksheetItEvaluations
    with WorksheetItAssertions {
  self: WorksheetRunTestSettings =>

  protected val (foldStart, foldEnd)                 = ("<folding>", "</folding>")
  protected val (foldStartExpanded, foldEndExpanded) = ("<foldingExpanded>", "</foldingExpanded>")

  override protected def supportedIn(version: ScalaVersion): Boolean = version > Scala_2_9

  protected def evaluationTimeout: Duration = 60 seconds

  protected implicit def project: Project = getProject

  protected def worksheetCache = WorksheetCache.getInstance(project)

  protected def worksheetFileName: String = s"worksheet_${getTestName(false)}.sc"

  protected def setupWorksheetSettings(settings: WorksheetCommonSettings): Unit = {
    settings.setRunType(self.runType)
    settings.setInteractive(false) // TODO: test these values?
    settings.setMakeBeforeRun(false)
  }

  protected final def worksheetSettings(worksheetEditor: Editor): WorksheetFileSettings = {
    val file = PsiDocumentManager.getInstance(project).getPsiFile(worksheetEditor.getDocument)
    WorksheetFileSettings(file)
  }

  protected def createCompilerProfileForCurrentModule(profileName: String): ScalaCompilerSettingsProfile =
    ScalaCompilerConfiguration.instanceIn(project).createCustomProfileForModule(profileName, myModule)

  override def setUp(): Unit = {
    super.setUp()

    val settings = ScalaProjectSettings.getInstance(project)
    settings.setInProcessMode(self.runInCompileServerProcess)
    settings.setAutoRunDelay(300)

    if (useCompileServer) {
      val result = CompileServerLauncher.ensureServerRunning(project)
      assertTrue("compile server is expected to be running", result)
    }
  }

  protected def doRenderTest(before: String, afterWithFoldings: String): Editor = {
    val beforeFixed = before
    val (afterFixed, foldings) = preprocessViewerText(afterWithFoldings)
    doRenderTest(beforeFixed, afterFixed, foldings)
  }

  protected def doRenderTest(editor: Editor, afterWithFoldings: String): Editor = {
    val (afterFixed, foldings) = preprocessViewerText(afterWithFoldings)
    doRenderTest(editor, afterFixed, foldings)
  }

  private def doRenderTest(
    before: String,
    after: String,
    foldings: Seq[Folding]
  ): Editor = {
    val TestRunResult(editor, evaluationResult) = doRenderTestWithoutCompilationChecks(before, after, foldings)

    evaluationResult shouldBe RunWorksheetActionResult.Done

    assertNoErrorMessages(editor)
    assertNoWarningMessages(editor)

    editor
  }

  private def doRenderTest(
    editor: Editor,
    after: String,
    foldings: Seq[Folding]
  ): Editor = {
    val TestRunResult(_, evaluationResult) = doRenderTestWithoutCompilationChecks(editor, after, foldings)

    evaluationResult shouldBe RunWorksheetActionResult.Done

    assertNoErrorMessages(editor)
    assertNoWarningMessages(editor)

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
    val result = runWorksheetEvaluationAndWait(before)
    assertViewerOutput(result.worksheetEditor)(after, foldings)
    result
  }

  private def doRenderTestWithoutCompilationChecks(
    editor: Editor,
    after: String,
    foldings: Seq[Folding]
  ): TestRunResult = {
    val result = runWorksheetEvaluationAndWait(editor)
    assertViewerOutput(editor)(after, foldings)
    result
  }

  protected def doFailingTest(text: String, expectedError: RunWorksheetActionResult.Error): Editor =
    doResultTest(text, expectedError)

  protected def doResultTest(text: String, expectedError: RunWorksheetActionResult): Editor = {
    val TestRunResult(editor, actualResult) = runWorksheetEvaluationAndWait(text)
    actualResult shouldBe expectedError
    editor
  }

  protected def doResultTest(editor: Editor, expectedError: RunWorksheetActionResult): Editor = {
    val actualResult = runWorksheetEvaluationAndWait(editor)
    actualResult.runResult shouldBe expectedError
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

  protected def viewerEditorDataFromLeftEditor(worksheetEditor: Editor): ViewerEditorData = {
    val data = Option(worksheetCache.getViewer(worksheetEditor)).map(viewerEditorData)
    data.getOrElse(fail("Viewer editor is empty").asInstanceOf[Nothing])
  }

  protected def viewerEditorData(viewer: Editor): ViewerEditorData = {
    val renderedText = viewer.getDocument.getText
    val foldings = viewer.getFoldingModel.getAllFoldRegions.map(Folding.apply)
    ViewerEditorData(viewer, renderedText, foldings)
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
