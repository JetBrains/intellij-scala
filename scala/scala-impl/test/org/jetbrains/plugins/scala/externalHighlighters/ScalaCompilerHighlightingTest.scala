package org.jetbrains.plugins.scala.externalHighlighters

import com.intellij.codeInsight.daemon.impl.{DaemonCodeAnalyzerImpl, HighlightInfo}
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.EdtTestUtil
import org.hamcrest.{Description, Matcher}
import org.jetbrains.plugins.scala.HighlightingTests
import org.jetbrains.plugins.scala.compilation.CompilerTestUtil.withErrorsFromCompiler
import org.jetbrains.plugins.scala.compiler.{CompilerEvent, CompilerEventListener}
import org.jetbrains.plugins.scala.debugger.ScalaCompilerTestBase
import org.jetbrains.plugins.scala.extensions.{HighlightInfoExt, invokeAndWait}
import org.jetbrains.plugins.scala.project.VirtualFileExt
import org.jetbrains.plugins.scala.util.matchers.{HamcrestMatchers, ScalaBaseMatcher}
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithScalaVersions, TestScalaVersion}
import org.junit.Assert.assertThat
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith

import scala.annotation.tailrec
import scala.jdk.CollectionConverters._
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Promise}
import scala.util.{Success, Try}

@RunWith(classOf[MultipleScalaVersionsRunner])
@Category(Array(classOf[HighlightingTests]))
class ScalaCompilerHighlightingTest
  extends ScalaCompilerTestBase
    with HamcrestMatchers {

  override def useCompileServer: Boolean = true
  override def runInDispatchThread: Boolean = false

  override def setUp(): Unit =
    EdtTestUtil.runInEdtAndWait(() => {
      ScalaCompilerHighlightingTest.super.setUp()
    })

  import ScalaCompilerHighlightingTest._

  @RunWithScalaVersions(Array(
    TestScalaVersion.Scala_2_13,
    TestScalaVersion.Scala_3_0
  ))
  def testWarningHighlighting(): Unit = runTestCase(
    fileName = "ExhaustiveMatchWarning.scala",
    content =
      """
        |class ExhaustiveMatchWarning {
        |  val option: Option[Int] = Some(1)
        |  option match {
        |    case Some(_) =>
        |  }
        |}
        |""".stripMargin,
    expectedResult = expectedResult(ExpectedHighlighting(
      severity = HighlightSeverity.WARNING,
      range = Some(new TextRange(70, 76)),
      msgPrefix = "match may not be exhaustive"
    ))
  )

  @RunWithScalaVersions(Array(
    TestScalaVersion.Scala_2_13,
    TestScalaVersion.Scala_3_0
  ))
  def testErrorHighlighting(): Unit = runTestCase(
    fileName = "AbstractMethodInClassError.scala",
    content =
      """
        |class AbstractMethodInClassError {
        |  def method: Int
        |}
        |""".stripMargin,
    expectedResult = expectedResult(ExpectedHighlighting(
      severity = HighlightSeverity.ERROR,
      range = Some(new TextRange(7, 33)),
      msgPrefix = "class AbstractMethodInClassError needs to be abstract"
    ))
  )

  private val worksheetContent =
    """42
      |val option: Option[Int] = Some(1)
      |option match {
      |  case Some(_) =>
      |}
      |unknownFunction()
      |val x = 23 //actually, in worksheets this should be treated as OK, but for now we just fix the behaviour in tests
      |val x = 23
      |"""

  /** see [[org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompiler.WrappedWorksheetCompilerMessagesFixer]] */
  @RunWithScalaVersions(Array(TestScalaVersion.Scala_2_13))
  def testOnlyErrorsAreExpectedInWorksheet_Scala_2_13(): Unit = runTestCaseForWorksheet(
    fileName = "worksheet.sc",
    content = worksheetContent.stripMargin,
    expectedResult = expectedResult(
      ExpectedHighlighting(
        severity = HighlightSeverity.ERROR,
        range = Some(new TextRange(72, 87)),
        msgPrefix = "not found: value unknownFunction"
      ),
      ExpectedHighlighting(
        severity = HighlightSeverity.ERROR,
        range = Some(new TextRange(208, 209)),
        msgPrefix = "x is already defined as value x"
      )
    )
  )

  /* see [[org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompiler.WrappedWorksheetCompilerMessagesFixer]] */
  @RunWithScalaVersions(Array(TestScalaVersion.Scala_3_0))
  def testOnlyErrorsAreExpectedInWorksheet_Scala_3(): Unit = runTestCaseForWorksheet(
    fileName = "worksheet.sc",
    content = worksheetContent.stripMargin,
    expectedResult = expectedResult(
      ExpectedHighlighting(
        severity = HighlightSeverity.ERROR,
        range = Some(new TextRange(72, 87)),
        msgPrefix = "Not found: unknownFunction"
      ),
      ExpectedHighlighting(
        severity = HighlightSeverity.ERROR,
        range = Some(new TextRange(208, 209)),
        msgPrefix = "Double definition:\nval x: Int in worksheet.sc at line 8 and\nval x: Int in worksheet.sc at line 9"
      )
    )
  )

  @RunWithScalaVersions(Array(TestScalaVersion.Scala_3_0))
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
        msgPrefix = "value foo is not a member of object X"
      ),
      ExpectedHighlighting(
        severity = HighlightSeverity.ERROR,
        range = Some(new TextRange(25, 28)),
        msgPrefix = "value bar is not a member of worksheet.sc"
      )
    )
  )


  private def runTestCase(
    fileName: String,
    content: String,
    expectedResult: ExpectedResult,
    waitUntilFileIsHighlighted: VirtualFile => Unit
  ): Unit = withErrorsFromCompiler {
    val virtualFile = addFileToProjectSources(fileName, content)
    waitUntilFileIsHighlighted(virtualFile)
    doAssertion(virtualFile, expectedResult)
  }

  private def doAssertion(virtualFile: VirtualFile,
                          expectedResult: ExpectedResult): Unit = {
    @tailrec
    def rec(attemptsLeft: Int): Unit = {
      val actualResult = invokeAndWait {
        val document = virtualFile.findDocument.get
        DaemonCodeAnalyzerImpl.getHighlights(document, null, getProject).asScala.toSeq
      }
      try {
        assertThat(actualResult, expectedResult)
      } catch {
        case error: AssertionError =>
          if (attemptsLeft > 0) {
            Thread.sleep(2000)
            rec(attemptsLeft - 1)
          } else {
            throw error
          }
      }
    }
    rec(2)
  }

  private def runTestCase(fileName: String,
                          content: String,
                          expectedResult: ExpectedResult): Unit = withErrorsFromCompiler {
    val waitUntilFileIsHighlighted: VirtualFile => Unit = virtualFile => {
      invokeAndWait {
        FileEditorManager.getInstance(getProject).openFile(virtualFile, true)
        compiler.rebuild()
      }
    }
    runTestCase(fileName, content, expectedResult, waitUntilFileIsHighlighted)
  }

  private def runTestCaseForWorksheet(
    fileName: String,
    content: String,
    expectedResult: ExpectedResult
  ): Unit = withErrorsFromCompiler {
    val waitUntilFileIsHighlighted: VirtualFile => Unit = virtualFile => {
      // Compilation is done on file opening (see RegisterCompilationListener.MyFileEditorManagerListener)
      // There is no explicit compile worksheet action for now, like we have in Build with JPS.
      // In order to detect the end of we wait until CompilationFinished event is generated
      val promise = Promise[Unit]()
      getProject.getMessageBus.connect().subscribe(CompilerEventListener.topic, new CompilerEventListener {
        override def eventReceived(event: CompilerEvent): Unit = event match {
          case CompilerEvent.CompilationFinished(_, files) =>
            // todo (minor): we should also ensure that the file is actually the tested file
            promise.complete(Success(()))
          case _ =>
            ()
        }
      })

      invokeAndWait {
        FileEditorManager.getInstance(getProject).openFile(virtualFile, true)
      }

      val timeout = 60.seconds
      Await.result(promise.future, timeout)
    }
    runTestCase(fileName, content, expectedResult, waitUntilFileIsHighlighted)
  }
}

object ScalaCompilerHighlightingTest {

  private type ExpectedResult = Matcher[Seq[HighlightInfo]]

  private case class ExpectedHighlighting(severity: HighlightSeverity,
                                          range: Option[TextRange] = None,
                                          msgPrefix: String = "")

  private def expectedResult(expected: ExpectedHighlighting*): ExpectedResult = new ScalaBaseMatcher[Seq[HighlightInfo]] {

    override protected def valueMatches(actualValue: Seq[HighlightInfo]): Boolean = {
      expected.size == actualValue.size &&
        expected.zip(actualValue).forall { case (expected, actual) =>
          actual.getSeverity == expected.severity &&
            expected.range.forall(_ == actual.range) &&
            actual.getDescription.startsWith(expected.msgPrefix)
        }
    }

    override protected def description: String =
      descriptionFor(expected)

    override def describeMismatch(item: Any, description: Description): Unit =
      item match {
        case seq: collection.Seq[HighlightInfo] =>
          val itemFixed = descriptionFor(seq.map(toExpectedHighlighting))
          super.describeMismatch(itemFixed, description)
        case _ =>
          super.describeMismatch(item, description)
      }

    private def toExpectedHighlighting(info: HighlightInfo): ExpectedHighlighting =
      ExpectedHighlighting(info.getSeverity, Some(info.range), info.getDescription)

    private def descriptionFor(highlightings: collection.Seq[ExpectedHighlighting]): String =
      highlightings.map(descriptionFor).mkString("\n")

    private def descriptionFor(highlighting: ExpectedHighlighting): String = {
      val ExpectedHighlighting(severity, range, msgPrefix) = highlighting
      val values = Seq(
        "severity" -> severity,
        "range" -> range.getOrElse("?"),
        "msgPrefix" -> msgPrefix
      ).map { case (name, value) =>
        s"$name=$value"
      }.mkString(",")
      s"HighlightInfo($values)"
    }
  }
}
