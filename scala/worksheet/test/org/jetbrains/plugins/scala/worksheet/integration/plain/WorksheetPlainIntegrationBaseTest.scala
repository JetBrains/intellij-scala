package org.jetbrains.plugins.scala.worksheet.integration.plain

import com.intellij.psi.PsiDocumentManager
import org.jetbrains.plugins.scala.WorksheetEvaluationTests
import org.jetbrains.plugins.scala.util.runners.{RunWithScalaVersions, TestScalaVersion}
import org.jetbrains.plugins.scala.worksheet.actions.topmenu.RunWorksheetAction.RunWorksheetActionResult.WorksheetRunError
import org.jetbrains.plugins.scala.worksheet.integration.util.{EditorRobot, MyUiUtils}
import org.jetbrains.plugins.scala.worksheet.integration.{WorksheetIntegrationBaseTest, WorksheetRunTestSettings, WorksheetRuntimeExceptionsTests}
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompiler.WorksheetCompilerResult
import org.jetbrains.plugins.scala.worksheet.settings.{WorksheetCommonSettings, WorksheetExternalRunType}
import org.junit.experimental.categories.Category

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

@Category(Array(classOf[WorksheetEvaluationTests]))
abstract class WorksheetPlainIntegrationBaseTest extends WorksheetIntegrationBaseTest
  with WorksheetRunTestSettings
  with WorksheetRuntimeExceptionsTests {

  override def runType: WorksheetExternalRunType = WorksheetExternalRunType.PlainRunType

  // with some health check runs
  @RunWithScalaVersions(extra = Array(
    TestScalaVersion.Scala_2_11_0,
    TestScalaVersion.Scala_2_12_0,
    TestScalaVersion.Scala_2_13_0,
  ))
  def testSimpleDeclaration(): Unit = {
    val left =
      """val a = 1
        |val b = 2
        |""".stripMargin

    val right =
      """a: Int = 1
        |b: Int = 2
        |""".stripMargin

    doRenderTest(left, right)
  }

  def testSimpleFolding(): Unit = {
    val left =
      """println("1\n2\n3")
        |val x = 42
        |""".stripMargin

    val right =
      s"""${foldStart}1
         |2
         |3
         |res0: Unit = ()$foldEnd
         |x: Int = 42
         |""".stripMargin

    doRenderTest(left, right)
  }

  def testMultipleFoldings(): Unit = {
    val left =
      """println("1\n2\n3")
        |val x = 42
        |println("4\n5\n6")
        |val y = 23
        |""".stripMargin

    val right =
      s"""${foldStart}1
         |2
         |3
         |res0: Unit = ()$foldEnd
         |x: Int = 42
         |${foldStart}4
         |5
         |6
         |res1: Unit = ()$foldEnd
         |y: Int = 23
         |""".stripMargin

    doRenderTest(left, right)
  }

  override def stackTraceLineStart = "\tat"

  override def exceptionOutputShouldBeExpanded = true

  def testDisplayFirstRuntimeException(): Unit = {
    val left =
      """println("1\n2")
        |
        |println(1 / 0)
        |
        |println(2 / 0)
        |""".stripMargin

    val right  =
      s"""${foldStart}1
        |2
        |res0: Unit = ()$foldEnd
        |
        |""".stripMargin

    val errorMessage = "java.lang.ArithmeticException: / by zero"
    testDisplayFirstRuntimeException(left, right, errorMessage)
  }

  def testCompilationError(): Unit = {
    val before =
      """val x = new A()
        |""".stripMargin

    val editor = doFailingTest(before, WorksheetRunError(WorksheetCompilerResult.CompilationError))

    // TODO: fix SCL-16497
    return
    assertCompilerMessages(editor)(
      """Error:(1, 13) not found: type A
        |val x = new A()
        |""".stripMargin.trim
    )
  }

  def testCompilationError_ContentLines(): Unit = {
    val before =
      """
        |
        |  val x = new A()
        |""".stripMargin

    val editor = doFailingTest(before, WorksheetRunError(WorksheetCompilerResult.CompilationError))

    // TODO: fix SCL-16497
    return
    assertCompilerMessages(editor)(
      """Error:(3, 15) not found: type A
        |val x = new A()
        |""".stripMargin.trim
    )
  }

  def testCompilationError_ContentIndentedInInnerScope(): Unit = {
    val before =
      """
        |class Outer {
        |  def foo = {
        |
        |    val x = new A()
        |  }
        |}
        |""".stripMargin

    val editor = doFailingTest(before, WorksheetRunError(WorksheetCompilerResult.CompilationError))

    // TODO: fix SCL-16497
    return
    assertCompilerMessages(editor)(
      """Error:(5, 17) not found: type A
        |val x = new A()
        |""".stripMargin.trim
    )
  }

  def testArrayRender(): Unit = {
    doRenderTest(
      """var a1 = new Array[Int](3)
        |val a2 = Array(1, 2, 3)""".stripMargin,
      """a1: Array[Int] = Array(0, 0, 0)
        |a2: Array[Int] = Array(1, 2, 3)
        |""".stripMargin
    )
  }

  def testInteractive(): Unit = {
    val editor = doRenderTest(
      """42""",
      """res0: Int = 42
        |""".stripMargin
    )
    val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument)
    WorksheetCommonSettings(file).setInteractive(true)

    val robot = new EditorRobot(editor)
    robot.moveToEnd()
    robot.typeString("\n23\n")

    // TODO: this is not the best way of testing, cause it relies on lucky threading conditions,
    //  but current architecture doesn't allow us do it some other way, think how this can be improved
    val stamp = editor.getDocument.getModificationStamp
    MyUiUtils.waitConditioned(5 seconds) { () =>
      editor.getDocument.getModificationStamp != stamp
    }

    assertViewerEditorText(editor)(
      """res0: Int = 42
        |res1: Int = 23
        |""".stripMargin
    )
    assertNoErrorMessages(editor)
  }

  def testInteractive_WithError(): Unit = {
    val editor = doRenderTest(
      """42""",
      """res0: Int = 42
        |""".stripMargin
    )
    val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument)
    WorksheetCommonSettings(file).setInteractive(true)

    val robot = new EditorRobot(editor)
    robot.moveToEnd()
    robot.typeString("\n2 + unknownRef + 4\n")

    MyUiUtils.wait(5 seconds)

    assertViewerEditorText(editor)(
      """res0: Int = 42
        |""".stripMargin
    )
    assertNoErrorMessages(editor)
  }
}
