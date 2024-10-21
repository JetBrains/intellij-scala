package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.fileEditor.{FileEditorManager, OpenFileDescriptor}
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.compiler.{CompilerEvent, CompilerEventListener}
import org.jetbrains.plugins.scala.extensions.invokeAndWait

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Promise}

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

  override protected def waitUntilFileIsHighlighted(virtualFile: VirtualFile): Unit = {
    // Compilation is done on file opening (see RegisterCompilationListener.MyFileEditorManagerListener)
    // There is no explicit compile worksheet action for now, like we have in Build with JPS.
    // In order to detect the end of we wait until CompilationFinished event is generated
    val promise = Promise[Unit]()
    getProject.getMessageBus.connect().subscribe(CompilerEventListener.topic, new CompilerEventListener {
      override def eventReceived(event: CompilerEvent): Unit = event match {
        case CompilerEvent.CompilationFinished(_, _, sources) =>
          val platformIndependentSources = sources.map(_.getCanonicalPath).map(FileUtil.toSystemIndependentName)
          val source = virtualFile.getCanonicalPath
          if (platformIndependentSources.contains(source)) {
            promise.success(())
          }
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
}


class ScalaWorksheetCompilerHighlightingTest_2_13 extends ScalaWorksheetCompilerHighlightingTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13

  def testOnlyErrorsAreExpectedInWorksheet(): Unit = runTestCase(
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

class ScalaWorksheetCompilerHighlightingTest_3 extends ScalaWorksheetCompilerHighlightingTestBase with CompilerDiagnosticsTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3

  /* see [[org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompiler.WrappedWorksheetCompilerMessagesFixer]] */
  def testOnlyErrorsAreExpectedInWorksheet(): Unit = runTestCase(
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

  def testReplaceWrapperClassNameFromErrorMessages(): Unit = runTestCase(
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

  def testCompilerDiagnostics(): Unit = {
    runCompilerDiagnosticsTest(
      fileName = "worksheet.sc",
      content =
        """def x: Int = 3
          |val test = x _
          |val y = 2 * x
          |""".stripMargin,
      expectedResult = expectedResult(
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          range = Some(TextRange.create(26, 29)),
          quickFixDescriptions = Seq("Rewrite to function value"),
          msgPrefix = "Only function types can be followed by _ but the current expression has type Int"
        )
      ),
      expectedContent =
        """def x: Int = 3
          |val test = (() => x)
          |val y = 2 * x
          |""".stripMargin
    )
  }

  def testMultipleCompilerDiagnosticsCorrectLines(): Unit = {
    runCompilerDiagnosticsTest(
      fileName = "worksheet.sc",
      content =
        """def m0 = "0"
          |def m00() = "00"
          |def m000(implicit s: String) = "000"
          |def m0000()(implicit s: String) = "0000"
          |
          |implicit val s: String = "42"
          |
          |val f1 = m0 _
          |val f2 = m00 _
          |val f3 = m000 _
          |val f4 = m0000 _
          |""".stripMargin,
      expectedResult = expectedResult(
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          range = Some(TextRange.create(149, 153)),
          quickFixDescriptions = Seq("Rewrite to function value"),
          msgPrefix = "Only function types can be followed by _ but the current expression has type String"
        ),
        ExpectedHighlighting(
          severity = HighlightSeverity.ERROR,
          range = Some(TextRange.create(178, 184)),
          quickFixDescriptions = Seq("Rewrite to function value"),
          msgPrefix = "Only function types can be followed by _ but the current expression has type String"
        )
      ),
      expectedContent =
        """def m0 = "0"
          |def m00() = "00"
          |def m000(implicit s: String) = "000"
          |def m0000()(implicit s: String) = "0000"
          |
          |implicit val s: String = "42"
          |
          |val f1 = (() => m0)
          |val f2 = m00 _
          |val f3 = (() => m000)
          |val f4 = m0000 _
          |""".stripMargin
    )
  }
}

class ScalaWorksheetCompilerHighlightingTest_3_5 extends ScalaWorksheetCompilerHighlightingTest_3 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_5
}

class ScalaWorksheetCompilerHighlightingTest_3_6 extends ScalaWorksheetCompilerHighlightingTest_3_5 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_6
}

class ScalaWorksheetCompilerHighlightingTest_3_RC extends ScalaWorksheetCompilerHighlightingTest_3 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_LTS_RC
}

class ScalaWorksheetCompilerHighlightingTest_3_Next_RC extends ScalaWorksheetCompilerHighlightingTest_3 {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_Next_RC
}
