package org.jetbrains.plugins.scala.debugger.evaluateExpression

import org.jetbrains.plugins.scala.debugger.ScalaDebuggerTestCase

/**
 * User: Alefas
 * Date: 17.10.11
 */

class ScalaFieldEvaluationTest extends ScalaDebuggerTestCase {
  def testStaticScalaFromPackageObject() {
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
      evalStartsWith("scala.math.Pi", "3.14")
    }
  }

  def testStaticScala() {
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
      evalStartsWith("scala.Predef.Map", "scala.collection.immutable.Map$")
    }
  }

  def testSimpleScala() {
    addFileToProject("Sample.scala",
      """
      |object Sample {
      |  val x = 23
      |  def main(args: Array[String]) {
      |    "stop here"
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 3)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalStartsWith("x", "23")
    }
  }

  def testSimpleJava() {
    addFileToProject("test/Java.java",
      """
      |package test;
      |public class Java {
      |  public int x = 23;
      |}
      """.stripMargin.trim()
    )

    addFileToProject("Sample.scala",
      """
      |object Sample {
      |  import test.Java
      |  val x = new Java
      |  def main(args: Array[String]) {
      |    "stop here"
      |  }
      |}
      """.stripMargin.trim()
    )
    addBreakpoint("Sample.scala", 4)
    runDebugger("Sample") {
      waitForBreakpoint()
      evalStartsWith("x.x", "23")
    }
  }


  def testStaticJava() {
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
      evalStartsWith("java.lang.Math.PI", "3.14")
    }
  }
}