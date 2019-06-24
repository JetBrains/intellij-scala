package org.jetbrains.plugins.scala
package debugger
package evaluateExpression

import org.jetbrains.plugins.scala.compiler.CompileServerLauncher
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


  addFileWithBreakpoints("FromPattern.scala",
    s"""
       |object FromPattern {
       |  def main(args: Array[String]) {
       |    Some("ab").map(x => (x, x + x)) match {
       |      case Some((a, b)) =>
       |        println(a)
       |        ""$bp
       |      case _ =>
       |    }
       |  }
       |}
      """.stripMargin.trim()
  )
  def testFromPattern(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("a", "ab")
      evalEquals("b", "abab")
    }
  }

  addFileWithBreakpoints("FromLibrary.scala",
    s"""object FromLibrary {
       |  def main(args: Array[String]): Unit = {
       |    val `this` = Seq(1, 2, 3, 4, 5)
       |    val that = Seq(6, 7, 8, 9, 10)
       |    `this` ++ that
       |  }
       |}
     """.stripMargin)
  def testFromLibrary(): Unit = {
    setupLibraryBreakpoint("scala.collection.TraversableLike", "++")
    runDebugger() {
      waitForBreakpoint()
      evalEquals("that", "List(6, 7, 8, 9, 10)")
      evalEquals("that.exists(_ => true)", "true")
      evalEquals("that.exists(_ => false)", "false")
      evalEquals("that.exists(_ => false)", "false")
      evalEquals("None.getOrElse(1)", "1")
    }
  }

  addSourceFile("InLambda.scala",
    s"""object InLambda {
       |  def main(args: Array[String]): Unit = {
       |    val list: List[Int] = List(1, 2, 3)
       |    list.map {x => println(x)}.toList
       |    System.out.println()
       |  }
       |}
      """.stripMargin.trim()
  )
  addBreakpoint(line = 3, "InLambda.scala", lambdaOrdinal = 0)
  def testInLambda(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("x", "1")
      evalEquals("None.getOrElse(x)", "1")

      resume()
      waitForBreakpoint()

      evalEquals("x", "2")
      evalEquals("None.getOrElse(x)", "2")
    }
  }
}
