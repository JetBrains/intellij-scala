package org.jetbrains.plugins.scala
package debugger
package evaluation

class FieldEvaluationTest_2_11 extends FieldEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_11
}

class FieldEvaluationTest_2_12 extends FieldEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_12
}

class FieldEvaluationTest_2_13 extends FieldEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13
}

class FieldEvaluationTest_3 extends FieldEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3
}

class FieldEvaluationTest_3_RC extends FieldEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3_RC
}

abstract class FieldEvaluationTestBase extends ExpressionEvaluationTestBase {
  addSourceFile("Static.scala",
    s"""
       |object Static {
       |  val x = 23
       |  private[this] val y = 1
       |
       |  def main(args: Array[String]): Unit = {
       |    println(y) $breakpoint
       |  }
       |}
    """.stripMargin.trim
  )
  def testStatic(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalStartsWith("x", "23")
      evalStartsWith("scala.math.Pi", "3.14") //from package object
      evalStartsWith("scala.Predef.Map", "scala.collection.immutable.Map$")
      evalStartsWith("java.lang.Math.PI", "3.14") //static java
      evalStartsWith("y", "1") //private this
    }
  }

  addSourceFile("test/Java.java",
    s"""
       |package test;
       |public class Java {
       |  public int x = 23;
       |  public static int y = 42;
       |}
    """.stripMargin.trim
  )
  addSourceFile("SimpleJava.scala",
    s"""
       |object SimpleJava {
       |  import test.Java
       |  val x = new Java
       |  def main(args: Array[String]): Unit = {
       |    println() $breakpoint
       |  }
       |}
    """.stripMargin.trim
  )
  def testSimpleJava(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalStartsWith("x.x", "23")
      evalStartsWith("Java.y", "42")
    }
  }

  addSourceFile("PrivateThisField.scala",
    s"""
       |object PrivateThisField {
       |  private[this] val x = 1
       |
       |  def main(args: Array[String]): Unit = {
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
       |    println(x + y + z + w) $breakpoint
       |  }
       |
       |  class KnowsYW {
       |    val z = y + w
       |  }
       |}
    """.stripMargin.trim
  )
  def testPrivateThisField(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalStartsWith("x", "0")
      evalStartsWith("y", "1")
      evalStartsWith("z", "2")
      evalStartsWith("w", "3")
    }
  }

  addSourceFile("NonStatic.scala",
    s"""
       |object NonStatic {
       |  def main(args: Array[String]): Unit = {
       |    val a = new A(2, 3)
       |    println() $breakpoint
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
    """.stripMargin.trim
  )
  def testNonStatic(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalStartsWith("a.x", "0")
      evalStartsWith("a.y", "1")
      evalStartsWith("a.z", "2")
      evalStartsWith("a.w", "3")
    }
  }

  addSourceFile("SimpleDynamicField.scala",
    s"""
       |class A1
       |class B1 extends A1 {val x = 23}
       |object SimpleDynamicField {
       |  val x: A1 = new B1
       |  def main(args: Array[String]): Unit = {
       |    println() $breakpoint
       |  }
       |}
      """.stripMargin.trim
  )
  def testSimpleDynamicField(): Unit = {
    expressionEvaluationTest() { implicit ctx =>
      evalStartsWith("x.x", "23")
    }
  }
}
