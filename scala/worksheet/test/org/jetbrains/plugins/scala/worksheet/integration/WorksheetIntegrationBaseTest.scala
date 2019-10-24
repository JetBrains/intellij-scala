package org.jetbrains.plugins.scala.worksheet.integration

import com.intellij.openapi.editor.{Editor, FoldRegion}
import com.intellij.openapi.fileEditor.{FileEditor, FileEditorManager, TextEditor}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.{EdtTestUtil, PlatformTestUtil}
import org.jetbrains.plugins.scala.compiler.CompileServerLauncher
import org.jetbrains.plugins.scala.debugger.ScalaCompilerTestBase
import org.jetbrains.plugins.scala.extensions.StringExt
import org.jetbrains.plugins.scala.worksheet.actions.topmenu.RunWorksheetAction
import org.jetbrains.plugins.scala.worksheet.integration.WorksheetIntegrationBaseTest.ExpectedFolding
import org.jetbrains.plugins.scala.worksheet.runconfiguration.WorksheetCache
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetCommonSettings
import org.jetbrains.plugins.scala.{ScalaVersion, Scala_2_10, Scala_2_11, Scala_2_12, Scala_2_13, SlowTests}
import org.junit.Assert._
import org.junit.experimental.categories.Category

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, TimeoutException}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

@Category(Array(classOf[SlowTests]))
abstract class WorksheetIntegrationBaseTest extends ScalaCompilerTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = Seq(
    Scala_2_10,
    Scala_2_11,
    Scala_2_12,
    Scala_2_13
  ).contains(version)

  protected def setupWorksheetSettings(settings: WorksheetCommonSettings): Unit

  override def setUp(): Unit = {
    super.setUp()

    if (useCompileServer){
      println("initializing compiler server")
      val result = CompileServerLauncher.ensureServerRunning(project)
      assertTrue("compile server is expected to be running", result)
    }
  }

  protected def project = getProject

  protected def doTest(
    before: String,
    after: String,
    foldings: Seq[ExpectedFolding] = Seq()
  ): Unit = {
    val (vFile, psiFile) = createWorksheetFile(before)

    val settings = WorksheetCommonSettings(psiFile)
    setupWorksheetSettings(settings)

    val worksheetEditor = openEditor(vFile)

    evaluateWorksheetAndWait(worksheetEditor)

    val cache = WorksheetCache.getInstance(project)
    val viewerEditor = cache.getViewer(worksheetEditor)

    val actualText = viewerEditor.getDocument.getText
    assertEquals(after.withNormalizedSeparator, actualText)

    val actualFoldings = viewerEditor.getFoldingModel.getAllFoldRegions
    assertFoldings(foldings, actualFoldings)

    // TODO: check the compiler output messages ?
    //  (should be empty for success runs, and non-empty for some warnings/failures)
  }

  private def createWorksheetFile(before: String) = {
    val fileName = s"worksheet_${getTestName(false)}.sc"

    val vFile = addFileToProjectSources(fileName, before)
    assertNotNull(vFile)

    val psiFile = PsiManager.getInstance(project).findFile(vFile)
    assertNotNull(psiFile)
    (vFile, psiFile)
  }

  private def openEditor(vFile: VirtualFile) = {
    val editors: Array[FileEditor] = EdtTestUtil.runInEdtAndGet { () =>
      FileEditorManager.getInstance(project).openFile(vFile, true)
    }

    editors match {
      case Array(e: Editor)     => e
      case Array(e: TextEditor) => e.getEditor
      case _                    => fail(s"couldn't fond any opened editor for file $vFile").asInstanceOf[Nothing]
    }
  }

  private def evaluateWorksheetAndWait(worksheetEditor: Editor): Unit = {
    // 3) run worksheet in a mode which is got from 1.2)
    // NOTE: worksheet backend / frontend initialization is done under the hood on calling action
    val future =
      RunWorksheetAction.runCompiler(project, worksheetEditor, auto = false)

    // 4) wait while worksheet evaluation is completed
    Try(Await.result(future, 60 seconds)) match {
      case Success(value)     =>
        println(value)
      case Failure(exception) =>
        exception match {
          case _: InterruptedException => fail("Thread was interrupted")
          case _: TimeoutException     => fail("Timeout period was exceeded while waiting for worksheet evaluation")
          case other                   => throw other
        }
    }

    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
  }

  private def assertFoldings(expectedFoldings: Seq[ExpectedFolding], actualFoldings: Seq[FoldRegion]): Unit = {
    expectedFoldings.zipAll(actualFoldings, null, null).toList.foreach { case (expected, actual) =>
      assertNotNull(
        s"""there are to few actual foldings:
           |expected : $expected
           |expected all : $expected
           |actual all : $actual
           |""".stripMargin,
        actual
      )
      assertNotNull(
        s"""there are some unexpected foldings:
           |actual: $actual
           |expected all : $expected
           |actual all : $actual
           |""".stripMargin,
        expected
      )
      assertFolding(expected, actual)
    }
  }

  private def assertFolding(expected: ExpectedFolding, actual: FoldRegion): Unit = {
    assertEquals(expected.startOffset, actual.getStartOffset)
    assertEquals(expected.endOffset, actual.getEndOffset)
    assertEquals(expected.isExpanded, actual.isExpanded)
    expected.placeholderText.foreach(assertEquals(_, actual.getPlaceholderText))
  }
}

object WorksheetIntegrationBaseTest {

  case class ExpectedFolding(
    startOffset: Int,
    endOffset: Int,
    placeholderText: Option[String] = None,
    isExpanded: Boolean = false
  )
}
