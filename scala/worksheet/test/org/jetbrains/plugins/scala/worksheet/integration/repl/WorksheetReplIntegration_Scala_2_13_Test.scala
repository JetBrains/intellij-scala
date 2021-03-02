package org.jetbrains.plugins.scala.worksheet.integration.repl

import com.intellij.openapi.editor.Editor
import org.jetbrains.plugins.scala.{DependencyManager, WorksheetEvaluationTests}
import org.jetbrains.plugins.scala.compilation.CompilerTestUtil.withModifiedRegistryValue
import org.jetbrains.plugins.scala.util.assertions.StringAssertions._
import org.jetbrains.plugins.scala.util.runners._
import org.jetbrains.plugins.scala.worksheet.actions.topmenu.RunWorksheetAction.RunWorksheetActionResult
import org.jetbrains.plugins.scala.worksheet.actions.topmenu.RunWorksheetAction.RunWorksheetActionResult.WorksheetRunError
import org.jetbrains.plugins.scala.worksheet.integration.WorksheetIntegrationBaseTest.TestRunResult
import org.jetbrains.plugins.scala.worksheet.integration.WorksheetRuntimeExceptionsTests.NoFolding
import org.jetbrains.plugins.scala.worksheet.integration.util.{EditorRobot, MyUiUtils}
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompiler.WorksheetCompilerResult
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompiler.WorksheetCompilerResult.RemoteServerConnectorError
import org.jetbrains.plugins.scala.worksheet.runconfiguration.WorksheetCache
import org.jetbrains.plugins.scala.worksheet.server.RemoteServerConnector
import org.jetbrains.plugins.scala.worksheet.server.RemoteServerConnector.RemoteServerConnectorResult.RequiredJLineIsMissingFromClasspathError
import org.jetbrains.plugins.scala.worksheet.ui.printers.WorksheetEditorPrinterRepl
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion, WorksheetEvaluationTests}
import org.junit.Assert._
import org.junit.experimental.categories.Category

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

@RunWithScalaVersions(Array(TestScalaVersion.Scala_2_13))
@Category(Array(classOf[WorksheetEvaluationTests]))
class WorksheetReplIntegration_Scala_2_13_Test extends WorksheetReplIntegration_Scala_2_12_Test {

  // with some health check runs
  @RunWithScalaVersions(Array(
    TestScalaVersion.Scala_2_13_0,
  ))
  def testSimpleDeclaration_2_13_0(): Unit = {
    /**
     * pre-download jline to avoid flaky tests on machines without locally-available jline (requires internet)
     * see org.jetbrains.plugins.scala.console.configuration.ScalaSdkJLineFixer for the details
     */
    import org.jetbrains.plugins.scala.DependencyManagerBase.RichStr
    DependencyManager.resolve("jline" % "jline" % "2.14.6")

    val left =
      """val a = 1
        |var b = 2
        |""".stripMargin

    val right =
      """a: Int = 1
        |b: Int = 2""".stripMargin

    doRenderTest(left, right)
  }

  override def testSimpleDeclaration(): Unit = {
    val left =
      """val a = 1
        |var b = 2
        |""".stripMargin

    val right =
      """val a: Int = 1
        |var b: Int = 2""".stripMargin

    doRenderTest(left, right)
  }

  override def testSimpleFolding(): Unit = {
    val left =
      """println("1\n2\n3")
        |val x = 42
        |""".stripMargin

    val right =
      s"""${foldStart}1
         |2
         |3$foldEnd
         |val x: Int = 42""".stripMargin

    doRenderTest(left, right)
  }

  override def testMultipleFoldings(): Unit = {
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
         |val x: Int = 42
         |${foldStart}4
         |5
         |6$foldEnd
         |val y: Int = 23""".stripMargin

    doRenderTest(left, right)
  }

  override def testTrimChunkOutputFromTheRightButNotFromTheLeft(): Unit = {
    val left =
      """println("\n\n1\n2\n3\n\n")
        |val x = 42
        |""".stripMargin

    val right =
      s"""$foldStart
         |
         |1
         |2
         |3$foldEnd
         |val x: Int = 42""".stripMargin

    doRenderTest(left, right)
  }

  override def testMultipleFoldings_WithSpacesBetweenSpaces(): Unit = {
    val left =
      """
        |println("1\n2\n3")
        |
        |
        |val x = 42
        |
        |
        |println("4\n5\n6")
        |
        |
        |val y = 23
        |""".stripMargin

    val right =
      s"""
         |${foldStart}1
         |2
         |3$foldEnd
         |
         |
         |val x: Int = 42
         |
         |
         |${foldStart}4
         |5
         |6$foldEnd
         |
         |
         |val y: Int = 23""".stripMargin

    doRenderTest(left, right)
  }

  override def testLongLineOutput(): Unit = {
    val left =
      """val text = "1\n^\n2\n3\n4\n^\n5\n6\n7\n8\n9"
        |val x = 42
        |""".stripMargin

    val right =
      s"""${foldStart}val text: String =
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
         |val x: Int = 42""".stripMargin

    doRenderTest(left, right)
  }

  override def testDisplayFirstRuntimeException(): Unit = {
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

    val exceptionOutputAssert: String => Unit = text => {
      assertStringMatches(
        text,
        """\Qjava.lang.ArithmeticException: / by zero\E
          |\s+...\s*\d+\s+elided\s*""".replace("\r", "").stripMargin.r
      )
    }
    val editor = testDisplayFirstRuntimeException(left, right, NoFolding, exceptionOutputAssert)
    assertLastLine(editor, 0)
    // run again with same editor, the output should be the same between these runs
    testDisplayFirstRuntimeException(editor, right, NoFolding, exceptionOutputAssert)
    assertLastLine(editor, 0)
  }

  private def assertLastLine(editor: Editor, line: Int): Unit = {
    val printer = worksheetCache.getPrinter(editor).get.asInstanceOf[WorksheetEditorPrinterRepl]
    assertEquals(
      "last processed line should point to last successfully evaluated line",
      Some(line), printer.lastProcessedLine
    )
  }

  override def testCompilationErrorsAndWarnings_ComplexTest(): Unit =
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


    val TestRunResult(editor, _) = doRenderTestWithoutCompilationChecks(before, after, WorksheetRunError(WorksheetCompilerResult.CompilationError))

    assertCompilerMessages(editor)(expectedCompilerOutput)

    val printer = worksheetCache.getPrinter(editor).get.asInstanceOf[WorksheetEditorPrinterRepl]
    assertEquals(
      "last processed line should point to last successfully-compiled and evaluated line",
      Some(1), printer.lastProcessedLine
    )
  }

  override def testArrayRender(): Unit = {
    doRenderTest(
      """var a1 = new Array[Int](3)
        |val a2 = Array(1, 2, 3)""".stripMargin,
      """var a1: Array[Int] = Array(0, 0, 0)
        |val a2: Array[Int] = Array(1, 2, 3)""".stripMargin
    )
  }

  override def testInteractive(): Unit = {
    val editor = doRenderTest(
      """42""",
      """val res0: Int = 42"""
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

    assertViewerEditorText(editor,
      """val res0: Int = 42
        |val res1: Int = 23""".stripMargin
    )

    assertNoErrorMessages(editor)
  }

  override def testInteractive_WithError(): Unit = {
    val editor = doRenderTest(
      """42""",
      """val res0: Int = 42"""
    )
    worksheetSettings(editor).setInteractive(true)

    val robot = new EditorRobot(editor)
    robot.moveToEnd()
    robot.typeString("\n2 + unknownRef + 4\n")

    MyUiUtils.wait(5 seconds)

    assertViewerEditorText(editor,
      """val res0: Int = 42
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

  // see SCL-11450
  override def testLambdaValueDefinitionOutputShouldBeFancy(): Unit = {
    val before = """val foo: String => Int = _.length"""

    doRenderTest(before, actual => {
      assertStringMatches(actual, """val foo: String => Int = <function\d*>[\d\w]*""".r)
    })
  }

  override def testSameWorksheetEvaluatedSeveralTimesShouldntAddAnyNewOutput(): Unit = {
    val before =
      """val x = 42
        |val y = 23""".stripMargin
    val after =
      """val x: Int = 42
        |val y: Int = 23""".stripMargin
    val worksheetEditor = prepareWorksheetEditor(before)
    runWorksheetEvaluationAndWait(worksheetEditor)
    assertViewerEditorText(worksheetEditor, after)
    runWorksheetEvaluationAndWait(worksheetEditor)
    assertViewerEditorText(worksheetEditor, after)
  }

  override def testSystemExit(): Unit =
    doRenderTest(
      """val x = 42
        |println(s"x: $x")
        |System.exit(0)""".stripMargin,
      """val x: Int = 42
        |x: 42""".stripMargin
    )

  override def testManyCompanionClassesAndObjects_WithVariousSpacesAndComments(): Unit = {
    val before =
      """val x = 1
        |
        |//
        |//
        |class A
        |
        |
        |/*
        | */
        |object A
        |
        |
        |/*
        | *
        | */
        |class X
        |
        |object X {
        |
        |}
        |
        |
        |/**
        |  *
        |  */
        |class Y {
        |
        |}
        |
        |object Y
        |
        |/*
        | */
        |class Z {
        |
        |}
        |
        |/////////////
        |object Z {
        |
        |}
        |
        |/////////////
        |
        |class XX
        |
        |object XX {
        |
        |}
        |
        |/////////////
        |
        |class YY {
        |
        |}
        |
        |object YY
        |
        |/////////////
        |
        |
        |class ZZ {
        |
        |}
        |
        |object ZZ {
        |
        |}""".stripMargin
    val after =
      """val x: Int = 1
        |
        |
        |
        |class A
        |
        |
        |
        |
        |object A
        |
        |
        |
        |
        |
        |class X
        |
        |object X
        |
        |
        |
        |
        |
        |
        |
        |class Y
        |
        |
        |
        |object Y
        |
        |
        |
        |class Z
        |
        |
        |
        |
        |object Z
        |
        |
        |
        |
        |
        |class XX
        |
        |object XX
        |
        |
        |
        |
        |
        |class YY
        |
        |
        |
        |object YY
        |
        |
        |
        |
        |class ZZ
        |
        |
        |
        |object ZZ""".stripMargin
    doRenderTest(before, after)
  }

  override def testManyCompanionClassesAndObjects_WithVariousTypeOfClasses(): Unit = {
    val before =
      """class C1
        |object C1
        |
        |abstract class C2
        |object C2
        |
        |final class C3
        |object C3
        |
        |trait T1
        |object T1
        |
        |sealed trait T2
        |object T2
        |
        |object O
        |trait O""".stripMargin
    val after  =
      """class C1
        |object C1
        |
        |class C2
        |object C2
        |
        |class C3
        |object C3
        |
        |trait T1
        |object T1
        |
        |trait T2
        |object T2
        |
        |object O
        |trait O""".stripMargin
    doRenderTest(before, after)
  }

  override def testSealedTraitHierarchy_1(): Unit = {
    val editor = doRenderTest(
      """sealed trait T""",
      """trait T"""
    )
    assertLastLine(editor, 0)
  }

  override def testSealedTraitHierarchy_2(): Unit = {
    val editor = doRenderTest(
      """sealed trait T
        |case class A() extends T""".stripMargin,
      """trait T
        |class A""".stripMargin
    )
    assertLastLine(editor, 1)
  }

  override def testSealedTraitHierarchy_3(): Unit = {
    val editor = doRenderTest(
      """sealed trait T
        |case class A() extends T
        |case class B() extends T""".stripMargin,
      """trait T
        |class A
        |class B""".stripMargin
    )
    assertLastLine(editor, 2)
  }

  override def testSealedTraitHierarchy_WithSpacesAndComments(): Unit = {
    val editor = doRenderTest(
      """sealed trait T
        |case class A() extends T
        |case class B() extends T
        |
        |//
        |//
        |case class C() extends T
        |
        |
        |/**
        |  *
        |  */
        |case class D() extends T""".stripMargin,
      """trait T
        |class A
        |class B
        |
        |
        |
        |class C
        |
        |
        |
        |
        |
        |class D""".stripMargin
    )
    assertLastLine(editor, 12)
  }

  override def testSealedTraitHierarchy_Several(): Unit = {
    val editor = doRenderTest(
      """sealed trait T1
        |
        |val x = 1
        |
        |sealed trait T2
        |case class A() extends T2
        |case class B() extends T2
        |
        |sealed trait T3
        |case class C() extends T3""".stripMargin,
      """trait T1
        |
        |val x: Int = 1
        |
        |trait T2
        |class A
        |class B
        |
        |trait T3
        |class C""".stripMargin
    )
    assertLastLine(editor, 9)
  }

  // yes, this is a very strange case, but anyway
  override def testSemicolonSeparatedExpressions(): Unit =
    doRenderTest(
      """val x = 23; val y = 42; def f(i: Int): String = "hello"; println("1\n2")""",
      s"""${foldStart}1
         |2
         |val x: Int = 23
         |val y: Int = 42
         |def f(i: Int): String$foldEnd""".stripMargin
    )

  override def testSemicolonSeparatedExpressions_OnMultipleLines(): Unit =
    doRenderTest(
      """val x = 23; val y =
        |  42; def f(i: Int): String = "hello"; println(
        |  "1\n2"
        |)""".stripMargin,
      s"""1
         |2
         |val x: Int = 23
         |${foldStart}val y: Int = 42
         |def f(i: Int): String$foldEnd""".stripMargin
    )

  override def testDoNoAddLineCommentsWithLineIndexesInsideMultilineStringLiterals(): Unit =
    doRenderTest(
      s"""val x =
         |  \"\"\"
         |    |\"\"\".stripMargin
         |x.length
         |val y: String =
         |  \"\"\"{
         |    |  "foo" : "bar"
         |    |}\"\"\".stripMargin
         |y.length
         |""".stripMargin,
      """val x: String =
        |"
        |"
        |val res0: Int = 1
        |val y: String =
        |{
        |  "foo" : "bar"
        |}
        |val res1: Int = 19""".stripMargin
    )

  private val LargeInputWithErrors =
    """var x =
      |      unknown1
      |
      |
      |/**
      |  *
      |  */
      |unknownVar = 23 +
      |  unknown2
      |
      |42 +
      |    unknown3 +
      |      unknown4
      |
      |println(
      |  "1" +
      |        unknown5
      |)
      |
      |val y =
      |  23; 42 +
      |    unknown6; val z =
      |      unknown7
      |
      |/**
      |  */
      |def foo: String = {
      |
      |    unknown8
      |}
      |
      |/**
      |  */
      |42 + {
      |    unknown9
      |}
      |
      |{
      |    unknown10
      |}
      |
      |
      |/**
      |  */
      |class X {
      |
      |
      |    unknown11
      |
      |      unknown12
      |}
      |
      |//
      |//comment
      |//
      |
      |object X {
      |
      |      unknown13
      |
      |
      |  unknown14
      |}""".stripMargin

  override def testRestoreErrorPositionsInOriginalFile(): Unit =
    withModifiedRegistryValue(RemoteServerConnector.WorksheetContinueOnFirstFailure, newValue = true).run {
      val expectedCompilerOutput =
        """Error:(2, 7) not found: value unknown1
          |unknown1
          |Error:(8, 1) not found: value unknownVar
          |unknownVar = 23 +
          |Error:(12, 5) not found: value unknown3
          |unknown3 +
          |Error:(13, 7) not found: value unknown4
          |unknown4
          |Error:(17, 9) not found: value unknown5
          |unknown5
          |Error:(22, 5) not found: value unknown6
          |unknown6; val z =
          |Error:(23, 7) not found: value unknown7
          |unknown7
          |Error:(29, 5) not found: value unknown8
          |unknown8
          |Error:(35, 5) not found: value unknown9
          |unknown9
          |Error:(39, 5) not found: value unknown10
          |unknown10
          |Error:(48, 5) not found: value unknown11
          |unknown11
          |Error:(50, 7) not found: value unknown12
          |unknown12
          |Error:(59, 7) not found: value unknown13
          |unknown13
          |Error:(62, 3) not found: value unknown14
          |unknown14
          |""".stripMargin

      val TestRunResult(editor, evaluationResult) =
        doRenderTestWithoutCompilationChecks2(LargeInputWithErrors, output => assertIsBlank(output))
      assertEquals(WorksheetRunError(WorksheetCompilerResult.CompilationError), evaluationResult)
      assertCompilerMessages(editor)(expectedCompilerOutput)
    }

  /** TODO: add tests for cases
   * 4. (minor) several evaluations of this isn't evaluated multiple times, it's broken now, if last statement has semicolon-separated expressions
   * {{{
   * val x = 42; val y =
   *   23
   * }}}
   *
   * 7. (SCL-17300) For scala 3
   * sealed trait T
   * case class A() extends T
   * case class B() extends T
   */
}
