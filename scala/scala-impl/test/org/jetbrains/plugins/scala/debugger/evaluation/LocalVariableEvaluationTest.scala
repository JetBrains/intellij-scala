package org.jetbrains.plugins.scala.debugger.evaluation

import org.jetbrains.plugins.scala.{DebuggerTests, LatestScalaVersions, ScalaVersion}
import org.junit.experimental.categories.Category

@Category(Array(classOf[DebuggerTests]))
class LocalVariableEvaluationTest_2_11 extends LocalVariableEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_11
}

@Category(Array(classOf[DebuggerTests]))
class LocalVariableEvaluationTest_2_12 extends LocalVariableEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_12
}

@Category(Array(classOf[DebuggerTests]))
class LocalVariableEvaluationTest_2_13 extends LocalVariableEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_13
}

@Category(Array(classOf[DebuggerTests]))
class LocalVariableEvaluationTest_3_0 extends LocalVariableEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_3_0
}

@Category(Array(classOf[DebuggerTests]))
class LocalVariableEvaluationTest_3_1 extends LocalVariableEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_3_1
}

abstract class LocalVariableEvaluationTestBase extends ExpressionEvaluationTestBase {
  addSourceFile("InMethod.scala",
    s"""object InMethod {
       |  class A { override def toString: String = "instance of A" }
       |  case class B() { override def toString: String = "instance of B" }
       |  trait C { override def toString: String = "instance of C" }
       |  object D { override def toString: String = "object D" }
       |  class V(private val n: Int) extends AnyVal { override def toString: String = s"V($$n)" }
       |
       |  def main(args: Array[String]): Unit = {
       |    val boolean = true
       |    val byte = 1.toByte
       |    val char = 'a'
       |    val double = 2.0
       |    val float = 3.0f
       |    val integer = 4
       |    val long = 5L
       |    val short = 6.toShort
       |    val string = "abc"
       |    val aInstance = new A()
       |    val bInstance = B()
       |    val cInstance = new C {}
       |    val d = D
       |    val value = new V(5)
       |    println() $breakpoint
       |  }
       |}
     """.stripMargin.trim
  )

  def testInMethod(): Unit = expressionEvaluationTest() { implicit ctx =>
    "boolean" evaluatesTo true
    "byte" evaluatesTo 1.toByte
    "char" evaluatesTo 'a'
    "double" evaluatesTo 2.0
    "float" evaluatesTo 3.0f
    "integer" evaluatesTo 4
    "long" evaluatesTo 5L
    "short" evaluatesTo 6.toShort
    "string" evaluatesTo "abc"
    "aInstance" evaluatesTo "instance of A"
    "bInstance" evaluatesTo "instance of B"
    "cInstance" evaluatesTo "instance of C"
    "d" evaluatesTo "object D"
    "value" evaluatesTo 5
  }

  addSourceFile("Destructuring.scala",
    s"""object Destructuring {
       |  final case class Person(name: String, age: Int)
       |
       |  object RemoveParenthesis {
       |    def unapply(s: String): Option[Int] =
       |      if (s.startsWith("(") && s.endsWith(")"))
       |        Some(s.substring(0, s.length() - 1).substring(1).toInt)
       |      else None
       |  }
       |
       |  def main(args: Array[String]): Unit = {
       |    val Person(name, age) = Person("Name", 25)
       |    val (one, two) = (1, 2)
       |    val RemoveParenthesis(n) = "(123)"
       |    println() $breakpoint
       |  }
       |}
     """.stripMargin.trim
  )

  def testDestructuring(): Unit = expressionEvaluationTest() { implicit ctx =>
    "name" evaluatesTo "Name"
    "age" evaluatesTo 25
    "one" evaluatesTo 1
    "two" evaluatesTo 2
    "n" evaluatesTo 123
  }

  addSourceFile("WalkStackFrames.scala",
    s"""object WalkStackFrames {
       |  def main(args: Array[String]): Unit = {
       |    val outer = 123
       |
       |    def first(): Unit = second()
       |    def second(): Unit = third()
       |    def third(): Unit = println() $breakpoint
       |
       |    first()
       |  }
       |}
     """.stripMargin.trim
  )

  def testWalkStackFrames(): Unit = expressionEvaluationTest() { implicit ctx =>
    "outer" evaluatesTo 123
  }

  addSourceFile("WalkStackFramesNested.scala",
    s"""object WalkStackFramesNested {
       |  def main(args: Array[String]): Unit = {
       |    val outer = 123
       |
       |    def first(): Unit = {
       |      def second(): Unit = {
       |        def third(): Unit = println() $breakpoint
       |
       |        third()
       |      }
       |
       |      second()
       |    }
       |
       |    first()
       |  }
       |}
     """.stripMargin.trim
  )

  def testWalkStackFramesNested(): Unit = expressionEvaluationTest() { implicit ctx =>
    "outer" evaluatesTo 123
  }
}
