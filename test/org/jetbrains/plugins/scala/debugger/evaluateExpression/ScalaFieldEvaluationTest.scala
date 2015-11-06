package org.jetbrains.plugins.scala.debugger.evaluateExpression

import org.jetbrains.plugins.scala.debugger.{ScalaDebuggerTestCase, ScalaVersion_2_11, ScalaVersion_2_12}

/**
 * User: Alefas
 * Date: 17.10.11
 */

class ScalaFieldEvaluationTest extends ScalaFieldEvaluationTestBase with ScalaVersion_2_11
class ScalaFieldEvaluationTest_212 extends ScalaFieldEvaluationTestBase with ScalaVersion_2_12

abstract class ScalaFieldEvaluationTestBase extends ScalaDebuggerTestCase {

  addFileWithBreakpoints("Static.scala",
   s"""
      |object Static {
      |  val x = 23
      |  private[this] val y = 1
      |
      |  def main(args: Array[String]) {
      |    ""$bp
      |  }
      |}
    """.stripMargin.trim()
  )
  def testStatic(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalStartsWith("x", "23")
      evalStartsWith("scala.math.Pi", "3.14") //from package object
      evalStartsWith("scala.Predef.Map", "scala.collection.immutable.Map$")
      evalStartsWith("java.lang.Math.PI", "3.14") //static java
      evalStartsWith("y", "1") //private this
    }
  }

  addFileWithBreakpoints("test/Java.java",
   s"""
      |package test;
      |public class Java {
      |  public int x = 23;
      |  public static int y = 42;
      |}
    """.stripMargin.trim()
  )
  addFileWithBreakpoints("SimpleJava.scala",
   s"""
      |object SimpleJava {
      |  import test.Java
      |  val x = new Java
      |  def main(args: Array[String]) {
      |    ""$bp
      |  }
      |}
    """.stripMargin.trim()
  )
  def testSimpleJava() {
    runDebugger() {
      waitForBreakpoint()
      evalStartsWith("x.x", "23")
      evalStartsWith("Java.y", "42")
    }
  }

  addFileWithBreakpoints("PrivateThisField.scala",
   s"""
      |object PrivateThisField {
      |  private[this] val x = 1
      |
      |  def main(args: Array[String]) {
      |    new PrivateThisField().foo()
      |  }
      |}
      |
      |class PrivateThisField {
      |  private[this] var x = 0
      |  private[this] var y = 1
      |  private[this] val z = 2
      |  private[this] val w = 3
      |
      |  def foo(): Unit = {
      |    ""$bp
      |  }
      |
      |  class KnowsYW {
      |    val z = y + w
      |  }
      |}
    """.stripMargin.trim()
  )
  def testPrivateThisField() {
    runDebugger() {
      waitForBreakpoint()
      evalStartsWith("x", "0")
      evalStartsWith("y", "1")
      evalStartsWith("z", "2")
      evalStartsWith("w", "3")
    }
  }

  addFileWithBreakpoints("NonStatic.scala",
   s"""
      |object NonStatic {
      |  def main(args: Array[String]) {
      |    val a = new A(2, 3)
      |    ""$bp
      |  }
      |}
      |
      |class A(val z: Int, var w: Int) extends B {
      |  var x = 0
      |  val y = 1
      |}
      |trait B {
      |  val t = 100
      |}
    """.stripMargin.trim()
  )
  def testNonStatic() {
    runDebugger() {
      waitForBreakpoint()
      evalStartsWith("a.x", "0")
      evalStartsWith("a.y", "1")
      evalStartsWith("a.z", "2")
      evalStartsWith("a.w", "3")
    }
  }

  addFileWithBreakpoints("SimpleDynamicField.scala",
    s"""
       |class A1
       |class B1 extends A1 {val x = 23}
       |object SimpleDynamicField {
       |  val x: A1 = new B1
       |  def main(args: Array[String]) {
       |    ""$bp
       |  }
       |}
      """.stripMargin.trim()
  )
  def testSimpleDynamicField() {
    runDebugger() {
      waitForBreakpoint()
      evalStartsWith("x.x", "23")
    }
  }
}