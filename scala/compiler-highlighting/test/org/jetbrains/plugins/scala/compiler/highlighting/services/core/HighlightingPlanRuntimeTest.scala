package org.jetbrains.plugins.scala.compiler.highlighting.services.core

import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInsight.lookup.{LookupArranger, LookupElement, LookupElementBuilder, LookupManager}
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.compiler.CompilerWorkspaceConfiguration
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.DumbModeTestUtils
import org.jetbrains.plugins.scala.compiler.highlighting.events.TriggerPhaseEvents.RequestId
import org.jetbrains.plugins.scala.compiler.highlighting.services.HighlightingPlanRuntime
import org.jetbrains.plugins.scala.compiler.highlighting.services.core.Planner.{PlannedWork, WorkKind}
import org.jetbrains.plugins.scala.compiler.highlighting.services.core.{CompilationStateWritesTestBase, ModuleKey}
import org.jetbrains.plugins.scala.compiler.highlighting.services.requests.{DocumentRequest, HighlightingRequest, RequestState, WorksheetRequest}
import org.jetbrains.plugins.scala.extensions.*
import org.junit.Assert.{assertEquals, assertFalse, assertNotNull, assertTrue}
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.{Before, Test}

import scala.compiletime.uninitialized

@RunWith(classOf[JUnit4])
class HighlightingPlanRuntimeTest extends CompilationStateWritesTestBase:

  private var runtime: HighlightingPlanRuntime = uninitialized

  override def setUp(): Unit =
    super.setUp()
    runtime = HighlightingPlanRuntime(getProject)

  @Before
  def testEdt():Unit = ApplicationManager.getApplication.assertIsDispatchThread()

  @Test
  def testInputsFor_IdentifiesWorksheetsAndScalaFilesCorrectly(): Unit =
    val scalaFile = inSources("Code.scala", "class Code")
    val worksheetFile = inSources("Script.sc", "println(1)")

    val reqId = RequestId(1, 123)
    val reqs = Seq(
      createDummyRequest(scalaFile, reqId),
      createDummyRequest(worksheetFile, reqId)
    )

    val inputs = runtime.inputsFor(reqs).toSeq

    assertEquals("Should extract inputs for both requests", 2, inputs.size)

    val scalaInput = inputs.find(_.request.file == scalaFile).get
    assertFalse("Standard Scala file should not be identified as a worksheet", scalaInput.isWorksheet)
    assertEquals(ModuleKey.of(getProject, scalaFile), scalaInput.target)

    val worksheetInput = inputs.find(_.request.file == worksheetFile).get
    assertTrue("Worksheet file should be identified as a worksheet", worksheetInput.isWorksheet)
    assertEquals(ModuleKey.of(getProject, worksheetFile), worksheetInput.target)

  @Test
  def testBuild_DocumentWork_CreatesDocumentRequest(): Unit =
    val scalaFile = inSources("Code.scala", "class Code")
    myFixture.openFileInEditor(scalaFile) // Ensures document exists

    val req = createDummyRequest(scalaFile, RequestId(1, 123))
    val input = runtime.inputsFor(Set(req)).toList
    val work = PlannedWork(WorkKind.Document, ::(input.head, input.tail), req.id)

    val request = runtime.build(work)

    assertTrue("Should build a DocumentRequest", request.collectFirst{
      case v:DocumentRequest => v
    }.isDefined)

  @Test
  def testBuild_WorksheetWork_CreatesWorksheetRequest(): Unit =
    val worksheetFile = inSources("Script.sc", "println(1)")
    myFixture.openFileInEditor(worksheetFile)

    val req = createDummyRequest(worksheetFile, RequestId(1, 123))
    val input = runtime.inputsFor(Seq(req)).toList
    val work = PlannedWork(WorkKind.Worksheet(needsModuleBuild = true), ::(input.head, input.tail), req.id)

    val request = runtime.build(work)

    assertTrue("Should build a WorksheetRequest", request.exists(_.is[WorksheetRequest]))
    assertTrue(request.get.asInstanceOf[WorksheetRequest].isFirstTimeHighlighting)

  @Test
  def testBuild_IncrementalWork_DelegatesToBuildManagerIfMakeOnSaveIsEnabled(): Unit =
    val scalaFile = inSources("Code.scala", "class Code")
    val req = createDummyRequest(scalaFile, RequestId(1, 123))
    val input = runtime.inputsFor(Seq(req)).toList
    val work = PlannedWork(WorkKind.Incremental(Set(scalaFile)), ::(input.head, input.tail), req.id)

    val config = CompilerWorkspaceConfiguration.getInstance(getProject)
    val originalSetting = config.MAKE_PROJECT_ON_SAVE

    try
      // MAKE_PROJECT_ON_SAVE = true -> Hands off to BuildManager, returns None
      config.MAKE_PROJECT_ON_SAVE = true
      assertTrue("Should return None when MAKE_PROJECT_ON_SAVE is active", runtime.build(work).isEmpty)

      // MAKE_PROJECT_ON_SAVE = false -> Returns standard Incremental request
      config.MAKE_PROJECT_ON_SAVE = false
      val request = runtime.build(work)
      assertTrue("Should return a CompilationRequest when MAKE_PROJECT_ON_SAVE is false", request.isDefined)
    finally
      config.MAKE_PROJECT_ON_SAVE = originalSetting

  @Test
  def testReadiness_ReturnsNotReadyWhenInDumbMode(): Unit =
    val scalaFile = inSources("Code.scala", "class Code")
    myFixture.openFileInEditor(scalaFile)

    val req = createDummyRequest(scalaFile, RequestId(1, 123))
    val input = runtime.inputsFor(Seq(req)).toList
    val work = PlannedWork(WorkKind.Document, ::(input.head, input.tail), req.id)

    DumbModeTestUtils.runInDumbModeSynchronously(
      getProject,
      () => {
        assertEquals(RequestState.NotReady, runtime.readiness(work))
      }
    )

  @Test
  def testReadiness_ReturnsNotReadyWhenTemplateIsActive(): Unit =
    val scalaFile = inSources("Code.scala", "class Code")
    myFixture.openFileInEditor(scalaFile)

    val req = createDummyRequest(scalaFile, RequestId(1, 123))
    val input = runtime.inputsFor(Seq(req)).toList
    val work = PlannedWork(WorkKind.Document,::(input.head, input.tail), req.id)
    val editor = myFixture.getEditor

    TemplateManagerImpl.setTemplateTesting(getTestRootDisposable)

    val manager = TemplateManager.getInstance(getProject)
    val template = manager.createTemplate("test", "test", "$VALUE$")
    template.addVariable("VALUE", "", "", true)

    manager.startTemplate(editor, template)

    assertNotNull(manager.getActiveTemplate(editor))
    assertEquals(RequestState.NotReady, runtime.readiness(work))

  @Test
  def testReadiness_ReturnsNotReadyWhenLookupIsOpen(): Unit =
    val scalaFile = inSources("Code.scala", "class Code")
    myFixture.openFileInEditor(scalaFile)

    val req = createDummyRequest(scalaFile, RequestId(1, 123))
    val input = runtime.inputsFor(Seq(req)).toList
    val work = PlannedWork(WorkKind.Document, ::(input.head, input.tail), req.id)
    val editor = myFixture.getEditor

    val items = Seq(
      LookupElementBuilder.create("first"),
      LookupElementBuilder.create("second")
    )

    val lookup = LookupManager
      .getInstance(getProject)
      .createLookup(editor, items.toArray, "", new LookupArranger.DefaultArranger())
      .asInstanceOf[LookupImpl]

    try
      lookup.showLookup()

      assertNotNull(LookupManager.getActiveLookup(editor))
      assertEquals(RequestState.NotReady, runtime.readiness(work))
    finally
      LookupManager.hideActiveLookup(getProject)

  private def createDummyRequest(vFile: VirtualFile, reqId: RequestId): HighlightingRequest =
    new HighlightingRequest:
      override val file: VirtualFile = vFile
      override val sequence: Long = 1
      override val id: RequestId = reqId
      override val debugReason: String = "test"