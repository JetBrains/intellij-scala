package org.jetbrains.plugins.scala.debugger.evaluateExpression

import org.jetbrains.plugins.scala.debugger.{ScalaDebuggerTestCase, ScalaVersion_2_11, ScalaVersion_2_12_M2}

/**
 * User: Alefas
 * Date: 19.10.11
 */

class ScalaLiteralEvaluationTest extends ScalaLiteralEvaluationTestBase with ScalaVersion_2_11
class ScalaLiteralEvaluationTest_2_12_M2 extends ScalaLiteralEvaluationTestBase with ScalaVersion_2_12_M2

abstract class ScalaLiteralEvaluationTestBase extends ScalaDebuggerTestCase {
  def testString() {
    addFileToProject("Sample.scala",
      """
      |object Sample {
      |  def main(args: Array[String]) {
      |    val n = 1
      |    "stop here"
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 3)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("\"x\".length", "1")
      evalEquals("s\"n = $n\"", "n = 1")
    }
  }

  def testLong() {
    addFileToProject("Sample.scala",
      """
      |object Sample {
      |  def main(args: Array[String]) {
      |    "stop here"
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 2)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("1L", "1")
    }
  }

  def testChar() {
    addFileToProject("Sample.scala",
      """
      |object Sample {
      |  def main(args: Array[String]) {
      |    "stop here"
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 2)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("'c'", "c")
    }
  }

  def testBoolean() {
    addFileToProject("Sample.scala",
      """
      |object Sample {
      |  def main(args: Array[String]) {
      |    "stop here"
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 2)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("true", "true")
    }
  }

  def testNull() {
    addFileToProject("Sample.scala",
      """
      |object Sample {
      |  def main(args: Array[String]) {
      |    "stop here"
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 2)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("null", "null")
    }
  }

  def testInt() {
    addFileToProject("Sample.scala",
      """
      |object Sample {
      |  def main(args: Array[String]) {
      |    "stop here"
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 2)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("1", "1")
    }
  }

  def testFloat() {
    addFileToProject("Sample.scala",
      """
        |object Sample {
        |  def main(args: Array[String]) {
        |    "stop here"
        |  }
        |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 2)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("1F", "1.0")
      evalEquals("Array(1F, 2.0F)", "[1.0,2.0]")
    }
  }

  def testImplicitConversions() {
    addFileToProject("Sample.scala",
      """
        |object Sample {
        |  implicit def intToString(x: Int) = x.toString + x.toString
        |  def main(args: Array[String]) {
        |    "stop here"
        |  }
        |}
      """.stripMargin.trim
    )
    addBreakpoint("Sample.scala", 3)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("123.charAt(3)", "1")
      evalEquals("\"a\".concat(123)", "a123123")
    }
  }

  def testSymbol() {
    addFileToProject("Sample.scala",
      """
        |object Sample {
        |  def main(args: Array[String]) {
        |    "stop here"
        |  }
        |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 2)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalEquals("'aaa", "'aaa")
      evalEquals("'aaa.name", "aaa")
    }
  }

}