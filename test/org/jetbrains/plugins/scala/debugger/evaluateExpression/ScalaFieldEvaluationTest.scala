package org.jetbrains.plugins.scala.debugger.evaluateExpression

/**
 * User: Alefas
 * Date: 17.10.11
 */

class ScalaFieldEvaluationTest extends ScalaDebuggerTestCase {
  def testStaticScalaFromPackageObject() {
    myFixture.addFileToProject("Sample.scala",
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
    myFixture.addFileToProject("Sample.scala",
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
    myFixture.addFileToProject("Sample.scala",
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
    myFixture.addFileToProject("test/Java.java",
      """
      |package test;
      |public class Java {
      |  public int x = 23;
      |}
      """.stripMargin.trim()
    )

    myFixture.addFileToProject("Sample.scala",
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
    myFixture.addFileToProject("Sample.scala",
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