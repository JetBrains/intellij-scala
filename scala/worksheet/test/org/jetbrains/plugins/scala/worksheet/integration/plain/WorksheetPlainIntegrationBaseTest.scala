package org.jetbrains.plugins.scala.worksheet.integration.plain

import com.intellij.psi.PsiDocumentManager
import org.jetbrains.plugins.scala.WorksheetEvaluationTests
import org.jetbrains.plugins.scala.extensions.StringExt
import org.jetbrains.plugins.scala.project.ModuleExt
import org.jetbrains.plugins.scala.util.runners.{RunWithScalaVersions, TestScalaVersion}
import org.jetbrains.plugins.scala.worksheet.actions.topmenu.RunWorksheetAction.RunWorksheetActionResult
import org.jetbrains.plugins.scala.worksheet.actions.topmenu.RunWorksheetAction.RunWorksheetActionResult.WorksheetRunError
import org.jetbrains.plugins.scala.worksheet.integration.WorksheetIntegrationBaseTest.{Folding, ViewerEditorData}
import org.jetbrains.plugins.scala.worksheet.integration.util.{EditorRobot, MyUiUtils}
import org.jetbrains.plugins.scala.worksheet.integration.{WorksheetIntegrationBaseTest, WorksheetRunTestSettings, WorksheetRuntimeExceptionsTests}
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompiler.WorksheetCompilerResult
import org.jetbrains.plugins.scala.worksheet.runconfiguration.WorksheetCache
import org.jetbrains.plugins.scala.worksheet.settings.{WorksheetExternalRunType, WorksheetFileSettings}
import org.jetbrains.plugins.scala.worksheet.ui.printers.WorksheetEditorPrinterPlain
import org.jetbrains.plugins.scala.worksheet.ui.printers.WorksheetEditorPrinterPlain.ViewerEditorState
import org.junit.Assert._
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

  // TODO: fix within SCL-16585
  override def exceptionOutputShouldBeExpanded = false

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
    val editor = testDisplayFirstRuntimeException(left, right, errorMessage)
    // run again with same editor, the output should be the same between these runs
    testDisplayFirstRuntimeException(editor, right, errorMessage)
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
    val viewer = WorksheetCache.getInstance(project).getViewer(editor)
    val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument)
    WorksheetFileSettings(file).setInteractive(true)

    val robot = new EditorRobot(editor)
    robot.moveToEnd()
    robot.typeString("\n23\n")

    // TODO: this is not the best way of testing, cause it relies on lucky threading conditions,
    //  but current architecture doesn't allow us do it some other way, think how this can be improved
    val stamp = viewer.getDocument.getModificationStamp
    MyUiUtils.waitConditioned(5 seconds) { () =>
      viewer.getDocument.getModificationStamp != stamp
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
    WorksheetFileSettings(file).setInteractive(true)

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

  def testAutoFlushOnLongEvaluation(): Unit = {
    val sleepTime = 1000
    val leftText =
      s"""println("a\\nb\\nc")
         |
         |def foo() = {
         |  for (i <- 1 to 3) {
         |    println(s"Hello $$i")
         |    Thread.sleep($sleepTime)
         |  }
         |}
         |
         |foo()
         |foo()
         |""".stripMargin

    val rightCommonText =
      s"""${foldStart}a
         |b
         |c
         |res0: Unit = ()$foldEnd
         |
         |foo: foo[]() => Unit
         |
         |
         |
         |
         |
         |
         |""".stripMargin

    //noinspection RedundantBlock
    val rightExtraMidFlushes = Array(
      s"Hello 1\n",
      s"${foldStart}Hello 1\nHello 2${foldEnd}\n",
      s"${foldStart}Hello 1\nHello 2\nHello 3${foldEnd}\n",
      s"${foldStart}Hello 1\nHello 2\nHello 3\nres1: Unit = ()${foldEnd}\nHello 1\n",
      s"${foldStart}Hello 1\nHello 2\nHello 3\nres1: Unit = ()${foldEnd}\n${foldStart}Hello 1\nHello 2$foldEnd\n",
      s"${foldStart}Hello 1\nHello 2\nHello 3\nres1: Unit = ()${foldEnd}\n${foldStart}Hello 1\nHello 2\nHello 3$foldEnd\n",
      s"${foldStart}Hello 1\nHello 2\nHello 3\nres1: Unit = ()${foldEnd}\n${foldStart}Hello 1\nHello 2\nHello 3\nres2: Unit = ()$foldEnd\n",
    )

    val rightTextStates: Array[String] = rightExtraMidFlushes.map(rightCommonText + _)

    val viewerStates = runLongEvaluation(leftText)

    viewerStates.zipAll(rightTextStates, null, null).zipWithIndex
      .foreach { case ((actualViewerState, expectedTextWithFoldings), idx) =>
        if (actualViewerState == null)
          fail(s"expected too many intermediate flushed:\n$expectedTextWithFoldings")
        if (expectedTextWithFoldings == null)
          fail(s"unexpected intermediate flush:\n$actualViewerState")

        val actualTextWithFoldings = renderViewerData(actualViewerState)
        assertEquals(s"Worksheet output at step $idx differs from expected",
          expectedTextWithFoldings.withNormalizedSeparator,
          actualTextWithFoldings
        )
      }
  }

  private def renderViewerData(viewerData: ViewerEditorData): String = {
    val text = viewerData.text
    val foldings = viewerData.foldings
    val builder = new java.lang.StringBuilder()

    val foldingsWithHelpers = Folding(0, 0, "") +: foldings :+ Folding(text.length, text.length, "")
    foldingsWithHelpers.sliding(2).foreach { case Seq(prev, Folding(startOffset, endOffset, placeholder, isExpanded)) =>
      builder.append(text, prev.endOffset, startOffset)
      val isHelperFolding = startOffset == endOffset && startOffset == text.length
      if (!isHelperFolding) {
        builder.append(if (isExpanded) foldStartExpanded else foldStart)
          .append(placeholder)
          //.append(text, startOffset, endOffset)
          .append(if (isExpanded) foldEndExpanded else foldEnd)
      }
    }
    builder.toString
  }

  private def runLongEvaluation(leftText: String): Seq[ViewerEditorData] = {
    val editor = prepareWorksheetEditor(leftText)

    val evaluationResult = waitForEvaluationEnd(runWorksheetEvaluation(editor))
    assertEquals(RunWorksheetActionResult.Done, evaluationResult)

    val printer = WorksheetCache.getInstance(project).getPrinter(editor)
      .getOrElse(fail("no printer found").asInstanceOf[Nothing]).asInstanceOf[WorksheetEditorPrinterPlain]
    val viewer = WorksheetCache.getInstance(project).getViewer(editor)

    val viewerStates: Seq[ViewerEditorData] =
      printer.viewerEditorStates.map { case ViewerEditorState(text, foldings) =>
        val foldingsConverted = foldings.map { case (start, end, placeholder, expanded) => Folding(start, end, placeholder, expanded) }
        ViewerEditorData(viewer, text, foldingsConverted)
      }

    viewerStates
  }

  private val TestProfileName = "TestProfileName"
  private val PartialUnificationCompilerOptions = Seq("-Ypartial-unification", "-language:higherKinds")
  private val PartialUnificationTestText =
    """def foo[F[_], A](fa: F[A]): String = "123"
      |foo { x: Int => x * 2 }
      |""".stripMargin

  // -Ypartial-unification is enabled in 2.13 by default, so testing on 2.12
  @RunWithScalaVersions(Array(TestScalaVersion.Scala_2_12))
  def testWorksheetShouldRespectCompilerSettingsFromCompilerProfile(): Unit = {
    val editor = prepareWorksheetEditor(PartialUnificationTestText, scratchFile = true)
    val profile = getModule.scalaCompilerSettingsProfile
    val newSettings = profile.getSettings.copy(
      additionalCompilerOptions = PartialUnificationCompilerOptions
    )
    profile.setSettings(newSettings)
    doRenderTest(editor,
      """foo: foo[F[_],A](val fa: F[A]) => String
        |res0: String = 123
        |""".stripMargin
    )
  }

  @RunWithScalaVersions(Array(TestScalaVersion.Scala_2_12))
  def testWorksheetShouldRespectCompilerSettingsFromCompilerProfile_WithoutSetting(): Unit = {
    val editor = prepareWorksheetEditor(PartialUnificationTestText, scratchFile = true)
    val profile = getModule.scalaCompilerSettingsProfile
    val newSettings = profile.getSettings.copy(
      additionalCompilerOptions = Seq.empty
    )
    profile.setSettings(newSettings)
    doResultTest(editor, RunWorksheetActionResult.WorksheetRunError(WorksheetCompilerResult.CompilationError))
  }

  @RunWithScalaVersions(Array(TestScalaVersion.Scala_2_12))
  def testWorksheetShouldRespectCompilerSettingsFromCompilerProfile_NonDefaultProfile(): Unit = {
    val editor = prepareWorksheetEditor(PartialUnificationTestText, scratchFile = true)
    worksheetSettings(editor).setCompilerProfileName(TestProfileName)
    val profile = createCompilerProfileForCurrentModule(TestProfileName)
    val newSettings = profile.getSettings.copy(
      additionalCompilerOptions = PartialUnificationCompilerOptions
    )
    profile.setSettings(newSettings)
    doRenderTest(editor,
      """foo: foo[F[_],A](val fa: F[A]) => String
        |res0: String = 123
        |""".stripMargin
    )
  }

  @RunWithScalaVersions(Array(TestScalaVersion.Scala_2_12))
  def testWorksheetShouldRespectCompilerSettingsFromCompilerProfile_WithoutSetting_NonDefaultProfile(): Unit = {
    val editor = prepareWorksheetEditor(PartialUnificationTestText, scratchFile = true)
    worksheetSettings(editor).setCompilerProfileName(TestProfileName)
    val profile = createCompilerProfileForCurrentModule(TestProfileName)
    val newSettings = profile.getSettings.copy(
      additionalCompilerOptions = Seq.empty
    )
    profile.setSettings(newSettings)
    doResultTest(editor, RunWorksheetActionResult.WorksheetRunError(WorksheetCompilerResult.CompilationError))
  }

  @RunWithScalaVersions(Array(TestScalaVersion.Scala_3_0))
  def testScala3_AllInOne(): Unit = {
    val before =
      """import java.io.PrintStream
        |import scala.concurrent.duration._;
        |import scala.collection.Seq;
        |
        |println(Seq(1, 2, 3))
        |println(1)
        |
        |()
        |23
        |"str"
        |
        |def foo = "123" + 1
        |def foo0 = 1
        |def foo1() = 1
        |def foo2: Int = 1
        |def foo3(): Int = 1
        |def foo4(p: String) = 1
        |def foo5(p: String): Int = 1
        |def foo6(p: String, q: Short): Int = 1
        |def foo7[T] = 1
        |def foo8[T]() = 1
        |def foo9[T]: Int = 1
        |def foo10[T](): Int = 1
        |def foo11[T](p: String) = 1
        |def foo12[T](p: String): Int = 1
        |def foo13[T](p: String, q: Short): Int = 1
        |
        |val _ = 1
        |val x = 2
        |val y = x.toString + foo
        |val x2: PrintStream = null
        |val q1 = new DurationInt(3)
        |var q2 = new DurationInt(4)
        |
        |def f = 11
        |var _ = 5
        |var v1 = 6
        |var v2 = v1 + f
        |v2 = v1
        |
        |class A
        |trait B
        |object B
        |
        |enum ListEnum[+A] {
        |  case Cons(h: A, t: ListEnum[A])
        |  case Empty
        |}
        |
        |println(ListEnum.Empty)
        |println(ListEnum.Cons(42, ListEnum.Empty))""".stripMargin
    val after =
      s"""
         |
         |
         |
         |${foldStart}List(1, 2, 3)
         |val res0: Unit = ()$foldEnd
         |${foldStart}1
         |val res1: Unit = ()${foldEnd}
         |
         |val res2: Unit = ()
         |val res3: Int = 23
         |val res4: String = str
         |
         |def foo: String
         |def foo0: Int
         |def foo1(): Int
         |def foo2: Int
         |def foo3(): Int
         |def foo4(p: String): Int
         |def foo5(p: String): Int
         |def foo6(p: String, q: Short): Int
         |def foo7[T]: Int
         |def foo8[T](): Int
         |def foo9[T]: Int
         |def foo10[T](): Int
         |def foo11[T](p: String): Int
         |def foo12[T](p: String): Int
         |def foo13[T](p: String, q: Short): Int
         |
         |
         |val x: Int = 2
         |val y: String = 21231
         |val x2: java.io.PrintStream = null
         |val q1: scala.concurrent.duration.package.DurationInt = scala.concurrent.duration.package$$DurationInt@3
         |var q2: scala.concurrent.duration.package.DurationInt = scala.concurrent.duration.package$$DurationInt@4
         |
         |def f: Int
         |
         |var v1: Int = 6
         |var v2: Int = 17
         |v2: Int = 6
         |
         |// defined class A
         |// defined trait B
         |// defined object B
         |
         |// defined enum ListEnum
         |
         |
         |
         |
         |${foldStart}Empty
         |val res5: Unit = ()${foldEnd}
         |${foldStart}Cons(42,Empty)
         |val res6: Unit = ()${foldEnd}
         |""".stripMargin
    doRenderTest(before, after)
  }
}
