package org.jetbrains.plugins.scala
package debugger.evaluateExpression

import org.jetbrains.plugins.scala.debugger.{ScalaDebuggerTestCase, ScalaVersion_2_11, ScalaVersion_2_12}

/**
 * Nikolay.Tropin
 * 8/2/13
 */

class InAnonFunEvaluationTest extends InAnonFunEvaluationTestBase with ScalaVersion_2_11

class InAnonFunEvaluationTest_212 extends InAnonFunEvaluationTestBase with ScalaVersion_2_12 {
  //todo SCL-9139
  override def testPartialFunction(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("a", "a")
      evalEquals("x", "x")
      evalEquals("param", "param")
      evalEquals("name", "name")
      evalEquals("notUsed", "notUsed")
      evalEquals("args", "[]")
    }
  }
}

abstract class InAnonFunEvaluationTestBase extends ScalaDebuggerTestCase{

  addFileWithBreakpoints("FunctionValue.scala",
    s"""
       |object FunctionValue {
       |  def main(args: Array[String]) {
       |    val a = "a"
       |    var b = "b"
       |    val f: (Int) => Unit = n => {
       |      val x = "x"
       |      ""$bp
       |    }
       |    f(10)
       |  }
       |}
      """.stripMargin.trim()
  )
  def testFunctionValue() {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("a", "a")
      evalEquals("b", "b")
      evalEquals("x", "x")
      evalEquals("n", "10")
      evalEquals("args", "[]")
    }
  }

  addFileWithBreakpoints("PartialFunction.scala",
    s"""
       |object PartialFunction {
       |  val name = "name"
       |  def main(args: Array[String]) {
       |    def printName(param: String, notUsed: String) {
       |      List(("a", 10)).foreach {
       |        case (a, i: Int) =>
       |            val x = "x"
       |            println(a + param)
       |            ""$bp
       |      }
       |    }
       |    printName("param", "notUsed")
       |  }
       |}
      """.stripMargin.trim()
  )
  def testPartialFunction() {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("a", "a")
      evalEquals("i", "10")
      evalEquals("x", "x")
      evalEquals("param", "param")
      evalEquals("name", "name")
      evalEquals("notUsed", "notUsed")
      evalEquals("args", "[]")
    }
  }

  addFileWithBreakpoints("FunctionExpr.scala",
    s"""
       |object FunctionExpr {
       |  val name = "name"
       |  def main(args: Array[String]) {
       |    def printName(param: String, notUsed: String) {
       |      List("a").foreach {
       |        a =>
       |            val x = "x"
       |            println(a + param)
       |            ""$bp
       |      }
       |    }
       |    printName("param", "notUsed")
       |  }
       |}
      """.stripMargin.trim()
  )
  def testFunctionExpr() {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("a", "a")
      evalEquals("x", "x")
      evalEquals("param", "param")
      evalEquals("name", "name")
      evalEquals("notUsed", "notUsed")
      evalEquals("args", "[]")
    }
  }

  addFileWithBreakpoints("ForStmt.scala",
    s"""
       |object ForStmt {
       |  val name = "name"
       |  def main(args: Array[String]) {
       |    def printName(param: String, notUsed: String) {
       |      for (s <- List("a", "b"); if s == "a"; ss = s + s; i <- List(1,2); if i == 1; si = s + i) {
       |        val in = "in"
       |        println(s + param + ss)
       |        ""$bp
       |      }
       |    }
       |    printName("param", "notUsed")
       |  }
       |}
      """.stripMargin.trim()
  )
  def testForStmt() {
    runDebugger() {
      waitForBreakpoint()
      evalEquals("s", "a")
      evalEquals("in", "in")
      evalEquals("param", "param")
      evalEquals("name", "name")
      evalEquals("notUsed", "notUsed")
      evalEquals("args", "[]")
      evalEquals("ss", "aa")
      evalEquals("i", ScalaBundle.message("not.used.from.for.statement", "i"))
      evalEquals("si", ScalaBundle.message("not.used.from.for.statement", "si"))
    }
  }

}
