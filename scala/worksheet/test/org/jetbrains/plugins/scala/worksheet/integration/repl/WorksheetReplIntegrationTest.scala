package org.jetbrains.plugins.scala.worksheet.integration.repl

import org.jetbrains.plugins.scala.{ScalaVersion, Scala_2_10, Scala_2_11, Scala_2_12, Scala_2_13, SlowTests}
import org.jetbrains.plugins.scala.worksheet.integration.WorksheetRuntimeExceptionsTests
import org.junit.experimental.categories.Category

import scala.language.postfixOps

@Category(Array(classOf[SlowTests]))
class WorksheetReplIntegrationTest extends WorksheetReplIntegrationBaseTest
  with WorksheetRuntimeExceptionsTests {

  override def compileInCompileServerProcess: Boolean = true

  override def runInCompileServerProcess: Boolean = true

  // FIXME: fails for scala 2.10:
  //  sbt.internal.inc.CompileFailed: Error compiling the sbt component 'repl-wrapper-2.10.7-55.0-2-ILoopWrapperImpl.jar'
  //  https://youtrack.jetbrains.com/issue/SCL-16175
  override protected def supportedIn(version: ScalaVersion): Boolean = Seq(
    Scala_2_11,
    Scala_2_12,
    Scala_2_13
  ).contains(version)

  def testSimpleDeclaration(): Unit = {
    val left =
      """val a = 1
        |val b = 2
        |""".stripMargin

    val right =
      """a: Int = 1
        |b: Int = 2""".stripMargin

    doTest(left, right)
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

    doTest(left, right)
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

    doTest(left, right)
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
}
