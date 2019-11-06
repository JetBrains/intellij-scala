package org.jetbrains.plugins.scala.worksheet.integration.repl

import org.jetbrains.plugins.scala.worksheet.actions.topmenu.RunWorksheetAction.RunWorksheetActionResult.WorksheetRunError
import org.jetbrains.plugins.scala.worksheet.integration.WorksheetIntegrationBaseTest.TestRunResult
import org.jetbrains.plugins.scala.worksheet.integration.WorksheetRuntimeExceptionsTests
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompiler.WorksheetCompilerResult
import org.jetbrains.plugins.scala.{ScalaVersion, Scala_2_11, WorksheetEvaluationTests}
import org.junit.Assert._
import org.junit.experimental.categories.Category

import scala.language.postfixOps

@Category(Array(classOf[WorksheetEvaluationTests]))
class WorksheetReplIntegrationTest extends WorksheetReplIntegrationBaseTest
  with WorksheetRuntimeExceptionsTests {

  // FIXME: fails for scala 2.10:
  //  sbt.internal.inc.CompileFailed: Error compiling the sbt component 'repl-wrapper-2.10.7-55.0-2-ILoopWrapperImpl.jar'
  //  https://youtrack.jetbrains.com/issue/SCL-16175
  override protected def supportedIn(version: ScalaVersion): Boolean = version >= Scala_2_11

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
        |println(2 / 0)
        |""".stripMargin

    val right =
      s"""${foldStart}1
         |2$foldEnd
         |
         |""".stripMargin

    val errorMessage = "java.lang.ArithmeticException: / by zero"

    testDisplayFirstRuntimeException(left, right, errorMessage)
  }

  def testCompilationErrorsAndWarnings_ComplexTst(): Unit = {
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

    assertCompilerMessages(editor)(
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
  }

  def testArrayRender(): Unit = {
    doRenderTest(
      """var a1 = new Array[Int](3)
        |val a2 = Array(1, 2, 3)""".stripMargin,
      """a1: Array[Int] = Array(0, 0, 0)
        |a2: Array[Int] = Array(1, 2, 3)""".stripMargin
    )
  }
}
