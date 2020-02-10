package org.jetbrains.plugins.scala.worksheet.integration.repl

import org.jetbrains.plugins.scala.project.ModuleExt
import org.jetbrains.plugins.scala.util.runners._
import org.jetbrains.plugins.scala.worksheet.actions.topmenu.RunWorksheetAction.RunWorksheetActionResult
import org.jetbrains.plugins.scala.worksheet.actions.topmenu.RunWorksheetAction.RunWorksheetActionResult.WorksheetRunError
import org.jetbrains.plugins.scala.worksheet.integration.WorksheetIntegrationBaseTest.TestRunResult
import org.jetbrains.plugins.scala.worksheet.integration.WorksheetRuntimeExceptionsTests
import org.jetbrains.plugins.scala.worksheet.integration.util.{EditorRobot, MyUiUtils}
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompiler.WorksheetCompilerResult
import org.jetbrains.plugins.scala.worksheet.runconfiguration.WorksheetCache
import org.jetbrains.plugins.scala.worksheet.ui.printers.WorksheetEditorPrinterRepl
import org.jetbrains.plugins.scala.{ScalaVersion, Scala_2_10, WorksheetEvaluationTests}
import org.junit.Assert._
import org.junit.experimental.categories.Category

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

@Category(Array(classOf[WorksheetEvaluationTests]))
class WorksheetReplIntegrationTest extends WorksheetReplIntegrationBaseTest
  with WorksheetRuntimeExceptionsTests {

  // FIXME: fails for scala 2.10:
  //  sbt.internal.inc.CompileFailed: Error compiling the sbt component 'repl-wrapper-2.10.7-55.0-2-ILoopWrapperImpl.jar'
  //  https://youtrack.jetbrains.com/issue/SCL-16175
  override protected def supportedIn(version: ScalaVersion): Boolean = version > Scala_2_10

  // with some health check runs
  @RunWithScalaVersions(extra = Array(
    //TestScalaVersion.Scala_2_10_0
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
        |b: Int = 2""".stripMargin

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
        |3$foldEnd
        |x: Int = 42""".stripMargin

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
         |3$foldEnd
         |x: Int = 42
         |${foldStart}4
         |5
         |6$foldEnd
         |y: Int = 23""".stripMargin

    doRenderTest(left, right)
  }

  def testLongLineOutput(): Unit = {
    val left =
      """val text = "1\n^\n2\n3\n4\n^\n5\n6\n7\n8\n9"
        |val x = 42
        |""".stripMargin

    val right =
      s"""${foldStart}text: String =
         |1
         |^
         |2
         |3
         |4
         |^
         |5
         |6
         |7
         |8
         |9$foldEnd
         |x: Int = 42""".stripMargin

    doRenderTest(left, right)
  }


  override def stackTraceLineStart = "..."

  override def exceptionOutputShouldBeExpanded = false

  def testDisplayFirstRuntimeException(): Unit = {
    val left =
      """println("1\n2")
        |
        |println(1 / 0)
        |
        |throw new RuntimeException
        |""".stripMargin

    val right =
      s"""${foldStart}1
         |2$foldEnd
         |
         |""".stripMargin

    val errorMessage = "java.lang.ArithmeticException: / by zero"

    val editor = testDisplayFirstRuntimeException(left, right, errorMessage)

    val printer = worksheetCache.getPrinter(editor).get.asInstanceOf[WorksheetEditorPrinterRepl]
    def assertLastLine(): Unit = assertEquals(
      "last processed line should point to last successfully evaluated line",
      Some(0), printer.getLastProcessedLine
    )

    assertLastLine()
    // run again with same editor, the output should be the same between these runs
    testDisplayFirstRuntimeException(editor, right, errorMessage)
    assertLastLine()
  }

  @NotSupportedScalaVersions(Array(TestScalaVersion.Scala_2_11))
  def testCompilationErrorsAndWarnings_ComplexTest(): Unit =
    baseTestCompilationErrorsAndWarnings_ComplexTest(
      """Warning:(2, 7) match may not be exhaustive.
        |It would fail on the following inputs: None, Some((x: Int forSome x not in 42))
        |Option(42) match {
        |
        |Error:(11, 13) not found: value Sum
        |def foo = Sum(Product(Number(2),
        |
        |Error:(11, 17) not found: value Product
        |def foo = Sum(Product(Number(2),
        |
        |Error:(11, 25) class java.lang.Number is not a value
        |def foo = Sum(Product(Number(2),
        |
        |Error:(12, 5) class java.lang.Number is not a value
        |Number(3)))
        |""".stripMargin.trim
    )

  @SupportedScalaVersions(Array(TestScalaVersion.Scala_2_11))
  def testCompilationErrorsAndWarnings_ComplexTest_2_11(): Unit =
    baseTestCompilationErrorsAndWarnings_ComplexTest(
      """Warning:(2, 7) match may not be exhaustive.
        |It would fail on the following inputs: None, Some((x: Int forSome x not in 42))
        |Option(42) match {
        |
        |Error:(11, 13) not found: value Sum
        |def foo = Sum(Product(Number(2),
        |
        |Error:(11, 17) not found: value Product
        |def foo = Sum(Product(Number(2),
        |
        |Error:(11, 25) object java.lang.Number is not a value
        |def foo = Sum(Product(Number(2),
        |
        |Error:(12, 5) object java.lang.Number is not a value
        |Number(3)))
        |""".stripMargin.trim

    )

  private def baseTestCompilationErrorsAndWarnings_ComplexTest(expectedCompilerOutput: String): Unit = {
    val before =
      """
        |Option(42) match {
        |  case Some(42) => println("1\n2\n3\n4")
        |}
        |
        |class X {
        |  sealed trait T
        |  case class A() extends T
        |  case class B() extends T
        |
        |  def foo = Sum(Product(Number(2),
        |    Number(3)))
        |}
        |
        |val shouldNotBeEvaluated = 42
        |""".stripMargin

    val after =
      s"""
         |1
         |2
         |${foldStart}3
         |4$foldEnd
         |
         |""".stripMargin


    val TestRunResult(editor, evaluationResult) = doRenderTestWithoutCompilationChecks(before, after)

    assertEquals(WorksheetRunError(WorksheetCompilerResult.CompilationError), evaluationResult)

    assertCompilerMessages(editor)(expectedCompilerOutput)

    val printer = worksheetCache.getPrinter(editor).get.asInstanceOf[WorksheetEditorPrinterRepl]
    assertEquals(
      "last processed line should point to last successfully-compiled and evaluated line",
      Some(1), printer.getLastProcessedLine
    )
  }

  def testArrayRender(): Unit = {
    doRenderTest(
      """var a1 = new Array[Int](3)
        |val a2 = Array(1, 2, 3)""".stripMargin,
      """a1: Array[Int] = Array(0, 0, 0)
        |a2: Array[Int] = Array(1, 2, 3)""".stripMargin
    )
  }

  def testInteractive(): Unit = {
    val editor = doRenderTest(
      """42""",
      """res0: Int = 42"""
    )
    worksheetSettings(editor).setInteractive(true)

    val robot = new EditorRobot(editor)
    robot.moveToEnd()
    robot.typeString("\n23\n")

    val viewer = WorksheetCache.getInstance(project).getViewer(editor)
    val stamp = viewer.getDocument.getModificationStamp
    MyUiUtils.waitConditioned(5 seconds) { () =>
      viewer.getDocument.getModificationStamp != stamp
    }

    assertViewerEditorText(editor)(
      """res0: Int = 42
        |res1: Int = 23""".stripMargin
    )

    assertNoErrorMessages(editor)
  }

  def testInteractive_WithError(): Unit = {
    val editor = doRenderTest(
      """42""",
      """res0: Int = 42"""
    )
    worksheetSettings(editor).setInteractive(true)

    val robot = new EditorRobot(editor)
    robot.moveToEnd()
    robot.typeString("\n2 + unknownRef + 4\n")

    MyUiUtils.wait(5 seconds)

    assertViewerEditorText(editor)(
      """res0: Int = 42
        |""".stripMargin
    )

    assertCompilerMessages(editor)(
      """Error:(2, 5) not found: value unknownRef
        |2 + unknownRef + 4""".stripMargin
    )
  }


  private def TestProfileName = "TestProfileName"
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
      """foo: [F[_], A](fa: F[A])String
        |res0: String = 123""".stripMargin
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
      """foo: [F[_], A](fa: F[A])String
        |res0: String = 123""".stripMargin
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
      """
        |
        |
        |
        |List(1, 2, 3)
        |1
        |
        |
        |val res0: Int = 23
        |val res1: String = str
        |
        |def foo: String
        |def foo0: Int
        |def foo1(): Int
        |def foo2: Int
        |def foo3(): Int
        |def foo4(p: String): Int
        |def foo5(p: String): Int
        |def foo6(p: String, q: Short): Int
        |def foo7[T] => Int
        |def foo8[T](): Int
        |def foo9[T] => Int
        |def foo10[T](): Int
        |def foo11[T](p: String): Int
        |def foo12[T](p: String): Int
        |def foo13[T](p: String, q: Short): Int
        |
        |
        |val x: Int = 2
        |val y: String = 21231
        |val x2: java.io.PrintStream = null
        |val q1: scala.concurrent.duration.package.DurationInt = 3
        |var q2: scala.concurrent.duration.package.DurationInt = 4
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
        |// defined class ListEnum
        |
        |
        |
        |
        |Empty
        |Cons(42,Empty)""".stripMargin
    doRenderTest(before, after)
  }
}
