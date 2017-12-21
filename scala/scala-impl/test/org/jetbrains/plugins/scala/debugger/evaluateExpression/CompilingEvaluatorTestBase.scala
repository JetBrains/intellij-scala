package org.jetbrains.plugins.scala.debugger.evaluateExpression

import org.jetbrains.plugins.scala.{DebuggerTests, SlowTests}
import org.jetbrains.plugins.scala.compiler.CompileServerLauncher
import org.jetbrains.plugins.scala.debugger._
import org.junit.experimental.categories.Category

/**
  * @author Nikolay.Tropin
  */

@Category(Array(classOf[DebuggerTests]))
class CompilingEvaluatorTest extends CompilingEvaluatorTestBase {
  override implicit val version: ScalaVersion = Scala_2_11
}

@Category(Array(classOf[DebuggerTests]))
class CompilingEvaluatorTest_212 extends CompilingEvaluatorTestBase {
  override implicit val version: ScalaVersion = Scala_2_12
}
abstract class CompilingEvaluatorTestBase extends ScalaDebuggerTestCase {

  override def setUp(): Unit = {
    super.setUp()
    CompileServerLauncher.ensureServerRunning(getProject)
  }

  addFileWithBreakpoints("SimplePlace.scala",
   s"""
      |object SimplePlace {
      |  val f = "f"
      |
      |  def foo(i: Int): Unit = {
      |    val x = 1
      |    ""$bp
      |  }
      |
      |  def main(args: Array[String]) {
      |    foo(3)
      |  }
      |}
    """.stripMargin.trim)

  def testSimplePlace(): Unit = {
    evaluateCodeFragments(
      "Seq(i, x).map(z => z * z).mkString(\", \")" -> "9, 1",

      """val result = for (z <- Seq(3, 4)) yield z * z
        |result.mkString
      """ -> "916",

      """def sqr(x: Int) = x * x
        |val a = sqr(12)
        |val b = sqr(1)
        |a + b
      """ -> "145",

      """Option(Seq(x)) match {
        |  case None => 1
        |  case Some(Seq(2)) => 2
        |  case Some(Seq(_)) => 0
        |}
      """ -> "0",

      """case class AAA(s: String, i: Int)
        |AAA("a", 1).toString
      """ -> "AAA(a,1)"

    )
  }

  addFileWithBreakpoints("InForStmt.scala",
   s"""
      |object InForStmt {
      |  def main(args: Array[String]) {
      |    for {
      |      x <- Seq(1, 2)
      |      if x == 2
      |    } {
      |      println()$bp
      |    }
      |  }
      |}
    """.stripMargin.trim
  )
  def testInForStmt(): Unit = {
    evaluateCodeFragments (
      "Seq(x, 2).map(z => z * z).mkString(\", \")" -> "4, 4",

      """def sqr(x: Int) = x * x
        |val a = sqr(12)
        |val b = sqr(1)
        |a + b
      """ -> "145"
    )
  }

  addFileWithBreakpoints("InConstructor.scala",
   s"""
      |object InConstructor {
      |  def main(args: Array[String]) {
      |    new Sample().foo()
      |  }
      |
      |  class Sample {
      |    val a = "a"
      |    val b = "b"$bp
      |
      |    def foo() = "foo"
      |  }
      |}
    """.stripMargin.trim
  )
  def testInConstructor(): Unit = {
    evaluateCodeFragments (
      "None.getOrElse(a)" -> "a",

      "foo().map(_.toUpper)" -> "FOO"
    )
  }

  addFileWithBreakpoints("AddBraces.scala",
 s"""
    |object AddBraces {
    |  def main(args: Array[String]) {
    |    foo()
    |  }
    |
    |  def foo(): String = "foo"$bp
    |}
  """.stripMargin.trim)
  def testAddBraces(): Unit = {
    evaluateCodeFragments(
      "None.getOrElse(foo())" -> "foo",

      """def bar = "bar"
        |foo() + bar
      """ -> "foobar"
    )
  }
}
