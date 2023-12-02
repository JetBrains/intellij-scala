package org.jetbrains.plugins.scala.worksheet.integration

import com.intellij.openapi.compiler.CompilerMessage
import com.intellij.openapi.editor.{Editor, FoldRegion}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.plugins.scala.compiler.{CompileServerLauncher, ScalaCompilerTestBase}
import org.jetbrains.plugins.scala.extensions.TextRangeExt
import org.jetbrains.plugins.scala.project.ModuleExt
import org.jetbrains.plugins.scala.project.settings.{ScalaCompilerConfiguration, ScalaCompilerSettingsProfile}
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.util.MarkersUtils
import org.jetbrains.plugins.scala.util.assertions.AssertionMatchers._
import org.jetbrains.plugins.scala.util.runners._
import org.jetbrains.plugins.scala.worksheet.WorksheetFile
import org.jetbrains.plugins.scala.worksheet.actions.topmenu.RunWorksheetAction.RunWorksheetActionResult
import org.jetbrains.plugins.scala.worksheet.integration.WorksheetIntegrationBaseTest._
import org.jetbrains.plugins.scala.worksheet.runconfiguration.WorksheetCache
import org.jetbrains.plugins.scala.worksheet.settings.persistent.WorksheetFilePersistentSettings
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion, WorksheetEvaluationTests}
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
  TODO 6: make tests methods more composible, there are just too many methods in this class now
*/
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_11,
  TestScalaVersion.Scala_2_12,
  TestScalaVersion.Scala_2_13,
))
@RunWithJdkVersions(Array(TestJdkVersion.JDK_1_8, TestJdkVersion.JDK_11, TestJdkVersion.JDK_17))
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

  override protected def supportedIn(version: ScalaVersion): Boolean = version > LatestScalaVersions.Scala_2_9

  protected def evaluationTimeout: Duration = 60 seconds

  protected implicit def project: Project = getProject

  protected def worksheetCache = WorksheetCache.getInstance(project)

  protected def worksheetFileName: String = s"worksheet_${getTestName(false)}.sc"

  override protected def reuseCompileServerProcessBetweenTests: Boolean =
    !SystemInfo.isMac || SystemInfo.OS_ARCH != "aarch64"

  protected def setupWorksheetSettings(settings: WorksheetFilePersistentSettings): Unit = {
    settings.setRunType(self.runType)
    settings.setInteractive(false) // TODO: test these values?
    settings.setMakeBeforeRun(false)
  }

  protected final def worksheetSettings(worksheetEditor: Editor): WorksheetFilePersistentSettings = {
    val file = PsiDocumentManager.getInstance(project).getPsiFile(worksheetEditor.getDocument)
    WorksheetFilePersistentSettings(file.getVirtualFile)
  }

  protected def createCompilerProfileForCurrentModule(profileName: String): ScalaCompilerSettingsProfile =
    ScalaCompilerConfiguration.instanceIn(project).createCustomProfileForModule(profileName, myModule)

  override def setUpProject(): Unit = {
    super.setUpProject()

    if (useCompileServer) {
      val result = CompileServerLauncher.ensureServerRunning(getProject)
      assertTrue("compile server is expected to be running", result)
    }
  }

  override def setUp(): Unit = {
    super.setUp()

    val settings = ScalaProjectSettings.getInstance(project)
    settings.setInProcessMode(self.runInCompileServerProcess)
    settings.setAutoRunDelay(300)
  }

  protected def doRenderTest(
    before: String,
    afterWithFoldings: String,
    isCompilerMessageAllowed: CompilerMessage => Boolean = _ => false
  ): WorksheetEditorAndFile = {
    val beforeFixed = before
    val (afterFixed, foldings) = preprocessViewerText(afterWithFoldings)
    doRenderTest(beforeFixed, afterFixed, foldings, isCompilerMessageAllowed)
  }

  protected def doRenderTestWithoutCompilationWarningsChecks(
    before: String,
    afterWithFoldings: String
  ): WorksheetEditorAndFile = {
    val beforeFixed = before
    val (afterFixed, foldings) = preprocessViewerText(afterWithFoldings)
    doRenderTestWithoutCompilationWarningsChecks(beforeFixed, afterFixed, foldings)
  }

  protected def doRenderTest(before: String, afterAssert: String => Unit): WorksheetEditorAndFile = {
    val TestRunResult(editor, evaluationResult) = doRenderTestWithoutCompilationChecks2(before, afterAssert)

    evaluationResult shouldBe RunWorksheetActionResult.Done

    assertNoErrorMessages(editor.editor)
    assertNoWarningMessages(editor.editor)

    editor
  }

  protected def doRenderTest(editorAndFile: WorksheetEditorAndFile, afterWithFoldings: String): Unit = {
    val (afterFixed, foldings) = preprocessViewerText(afterWithFoldings)
    doRenderTest(editorAndFile, afterFixed, foldings)
  }

  private def doRenderTest(
    before: String,
    after: String,
    foldings: Seq[Folding],
    isCompilerMessageAllowed: CompilerMessage => Boolean
  ): WorksheetEditorAndFile = {
    val TestRunResult(editor, _) =
      doRenderTestWithoutCompilationChecks(before, after, foldings, RunWorksheetActionResult.Done)

    assertNoErrorMessages(editor.editor, isCompilerMessageAllowed)
    assertNoWarningMessages(editor.editor, isCompilerMessageAllowed)

    editor
  }

  private def doRenderTestWithoutCompilationWarningsChecks(
    before: String,
    after: String,
    foldings: Seq[Folding]
  ): WorksheetEditorAndFile = {
    val TestRunResult(editor, _) =
      doRenderTestWithoutCompilationChecks(before, after, foldings, RunWorksheetActionResult.Done)

    assertNoErrorMessages(editor.editor)

    editor
  }

  private def doRenderTest(
    editorAndFile: WorksheetEditorAndFile,
    after: String,
    foldings: Seq[Folding]
  ): Unit = {
    val TestRunResult(_, evaluationResult) = doRenderTestWithoutCompilationChecks(editorAndFile, after, foldings)

    evaluationResult shouldBe RunWorksheetActionResult.Done

    assertNoErrorMessages(editorAndFile.editor)
    assertNoWarningMessages(editorAndFile.editor)
  }

  protected def doRenderTestWithoutCompilationChecks(
    before: String,
    afterWithFoldings: String,
    expectedEvaluationResult: RunWorksheetActionResult
  ): TestRunResult = {
    val beforeFixed = before
    val (afterFixed, foldings) = preprocessViewerText(afterWithFoldings)
    doRenderTestWithoutCompilationChecks(beforeFixed, afterFixed, foldings, expectedEvaluationResult)
  }

  private def doRenderTestWithoutCompilationChecks(
    before: String,
    after: String,
    foldings: Seq[Folding],
    expectedEvaluationResult: RunWorksheetActionResult
  ): TestRunResult = {
    val result = runWorksheetEvaluationAndWait(before)

    assertEquals("evaluation result is wrong", expectedEvaluationResult, result.runResult)
    assertViewerOutput(result.editorAndFile.editor, after, foldings)

    result
  }

  protected def doRenderTestWithoutCompilationChecks2(
    before: String,
    afterAssert: String => Unit
  ): TestRunResult = {
    val result = runWorksheetEvaluationAndWait(before)
    assertViewerEditorText(result.editorAndFile.editor, afterAssert)
    result
  }

  protected def doRenderTestWithoutCompilationChecks(
    editorAndFile: WorksheetEditorAndFile,
    afterWithFoldings: String,
  ): TestRunResult = {
    val (afterFixed, foldings) = preprocessViewerText(afterWithFoldings)
    val result = runWorksheetEvaluationAndWait(editorAndFile)
    assertViewerOutput(editorAndFile.editor, afterFixed, foldings)
    result
  }

  private def doRenderTestWithoutCompilationChecks(
    editorAndFile: WorksheetEditorAndFile,
    after: String,
    foldings: Seq[Folding]
  ): TestRunResult = {
    val result = runWorksheetEvaluationAndWait(editorAndFile)
    assertViewerOutput(editorAndFile.editor, after, foldings)
    result
  }

  protected def doFailingTest(text: String, expectedError: RunWorksheetActionResult.Error): WorksheetEditorAndFile =
    doResultTest(text, expectedError)

  protected def doResultTest(text: String, expectedError: RunWorksheetActionResult): WorksheetEditorAndFile = {
    val TestRunResult(editor, actualResult) = runWorksheetEvaluationAndWait(text)
    actualResult shouldBe expectedError
    editor
  }

  protected def doResultTest(editor: WorksheetEditorAndFile, expectedError: RunWorksheetActionResult): WorksheetEditorAndFile = {
    val actualResult = runWorksheetEvaluationAndWait(editor)
    actualResult.runResult shouldBe expectedError
    editor
  }

  protected def preprocessViewerText(text: String): (String, Seq[Folding]) = {
    val (textFixed, ranges) = {
      val markers = IndexedSeq((foldStart, foldEnd), (foldStartExpanded, foldEndExpanded))
      MarkersUtils.extractMarkers(text, markers)
    }
    val foldings = ranges.map { case (TextRangeExt(startOffset, endOffset), markerType) =>
      Folding(startOffset, endOffset, isExpanded = markerType == 1)
    }
    (textFixed, foldings)
  }

  protected def viewerEditorDataFromLeftEditor(worksheetEditor: Editor): ViewerEditorData = {
    val data = Option(worksheetCache.getViewer(worksheetEditor)).map(viewerEditorData)
    data.getOrElse(fail("Viewer editor is empty").asInstanceOf[Nothing])
  }

  protected def viewerEditorData(viewer: Editor): ViewerEditorData = {
    val renderedText = viewer.getDocument.getText
    val foldRegions = viewer.getFoldingModel.getAllFoldRegions
    val foldings = foldRegions.map(Folding.apply)
    ViewerEditorData(viewer, renderedText, foldings.toIndexedSeq)
  }

  protected def setAdditionalCompilerOptions(options: Seq[String]): Unit = {
    val profile = getModule.scalaCompilerSettingsProfile
    val newSettings = profile.getSettings.copy(additionalCompilerOptions = options)
    profile.setSettings(newSettings)
  }
}

object WorksheetIntegrationBaseTest {

  case class WorksheetEditorAndFile(editor: Editor, psiFile: WorksheetFile)

  case class TestRunResult(
    editorAndFile: WorksheetEditorAndFile,
    runResult: RunWorksheetActionResult
  )

  case class ViewerEditorData(
    editor: Editor,
    text: String,
    foldings: Seq[Folding]
  )

  // placeholder text isn't tested, but it actually has some construction logic which limits placeholder length:
  // see org.jetbrains.plugins.scala.worksheet.ui.WorksheetFoldGroup.addRegion
  case class Folding(
    startOffset: Int,
    endOffset: Int,
    isExpanded: Boolean = false
  )

  object Folding {

    def apply(region: FoldRegion): Folding =
      Folding(region.getStartOffset, region.getEndOffset, region.isExpanded)
  }
}
