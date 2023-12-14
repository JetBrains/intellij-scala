package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.fileEditor.{FileEditorManager, OpenFileDescriptor}
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.compiler.{CompilerEvent, CompilerEventListener}
import org.jetbrains.plugins.scala.extensions.invokeAndWait
import org.jetbrains.plugins.scala.util.CompilerTestUtil.runWithErrorsFromCompiler

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Promise}
import scala.util.Success

abstract class ScalaWorksheetCompilerHighlightingTestBase extends ScalaCompilerHighlightingTestBase {

  protected val worksheetContent =
    """42
      |val option: Option[Int] = Some(1)
      |option match {
      |  case Some(_) =>
      |}
      |unknownFunction()
      |val x = 23 //actually, in worksheets this should be treated as OK, but for now we just fix the behaviour in tests
      |val x = 23
      |""".stripMargin

  protected def runTestCaseForWorksheet(
    fileName: String,
    content: String,
    expectedResult: ExpectedResult
  ): Unit = runWithErrorsFromCompiler(getProject) {
    val waitUntilFileIsHighlighted: VirtualFile => Unit = virtualFile => {
      // Compilation is done on file opening (see RegisterCompilationListener.MyFileEditorManagerListener)
      // There is no explicit compile worksheet action for now, like we have in Build with JPS.
      // In order to detect the end of we wait until CompilationFinished event is generated
      val promise = Promise[Unit]()
      getProject.getMessageBus.connect().subscribe(CompilerEventListener.topic, new CompilerEventListener {
        override def eventReceived(event: CompilerEvent): Unit = event match {
          case CompilerEvent.CompilationFinished(_, _, _) =>
            // todo (minor): we should also ensure that the file is actually the tested file
            promise.complete(Success(()))
          case _ =>
            ()
        }
      })

      invokeAndWait {
        val descriptor = new OpenFileDescriptor(getProject, virtualFile)
        val editor = FileEditorManager.getInstance(getProject).openTextEditor(descriptor, true)
        // The tests are running in a headless environment where focus events are not propagated.
        // We need to call our listener manually.
        new CompilerHighlightingEditorFocusListener(editor).focusGained()
      }

      val timeout = 60.seconds
      Await.result(promise.future, timeout)
    }
    runTestCase(fileName, content, expectedResult, waitUntilFileIsHighlighted)
  }
}


class ScalaWorksheetCompilerHighlightingTest_2_13 extends ScalaWorksheetCompilerHighlightingTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13

  def testOnlyErrorsAreExpectedInWorksheet(): Unit = runTestCaseForWorksheet(
    fileName = "worksheet.sc",
    content = worksheetContent.stripMargin,
    expectedResult = expectedResult(
      ExpectedHighlighting(
        severity = HighlightSeverity.ERROR,
        range = Some(new TextRange(72, 87)),
        quickFixDescriptions = Nil,
        msgPrefix = "not found: value unknownFunction"
      ),
      ExpectedHighlighting(
        severity = HighlightSeverity.ERROR,
        range = Some(new TextRange(208, 209)),
        quickFixDescriptions = Nil,
        msgPrefix = "x is already defined as value x"
      )
    )
  )
}

class ScalaWorksheetCompilerHighlightingTest_3_0 extends ScalaWorksheetCompilerHighlightingTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_0

  /* see [[org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompiler.WrappedWorksheetCompilerMessagesFixer]] */
  def testOnlyErrorsAreExpectedInWorksheet(): Unit = runTestCaseForWorksheet(
    fileName = "worksheet.sc",
    content = worksheetContent.stripMargin,
    expectedResult = expectedResult(
      ExpectedHighlighting(
        severity = HighlightSeverity.ERROR,
        range = Some(new TextRange(72, 87)),
        quickFixDescriptions = Nil,
        msgPrefix = "Not found: unknownFunction"
      ),
      ExpectedHighlighting(
        severity = HighlightSeverity.ERROR,
        range = Some(new TextRange(208, 209)),
        quickFixDescriptions = Nil,
        msgPrefix = "Double definition:\nval x: Int in worksheet.sc at line 8 and\nval x: Int in worksheet.sc at line 9"
      )
    )
  )

  def testReplaceWrapperClassNameFromErrorMessages(): Unit = runTestCaseForWorksheet(
    fileName = "worksheet.sc",
    content =
      """object X {}
        |X.foo()
        |this.bar()""".stripMargin,
    expectedResult = expectedResult(
      ExpectedHighlighting(
        severity = HighlightSeverity.ERROR,
        range = Some(new TextRange(14, 17)),
        quickFixDescriptions = Nil,
        msgPrefix = "value foo is not a member of object X"
      ),
      ExpectedHighlighting(
        severity = HighlightSeverity.ERROR,
        range = Some(new TextRange(25, 28)),
        quickFixDescriptions = Nil,
        msgPrefix = "value bar is not a member of worksheet.sc"
      )
    )
  )
}
