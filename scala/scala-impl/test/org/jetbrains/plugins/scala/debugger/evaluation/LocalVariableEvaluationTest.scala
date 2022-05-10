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
       |""".stripMargin.trim
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

  addSourceFile("InLambda.scala",
    s"""object InLambda {
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
       |""".stripMargin.trim
  )

  def testInLambda(): Unit = expressionEvaluationTest() { implicit ctx =>
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
       |""".stripMargin.trim
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
       |""".stripMargin.trim
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
       |""".stripMargin.trim
  )

  def testWalkStackFramesNested(): Unit = expressionEvaluationTest() { implicit ctx =>
    "outer" evaluatesTo 123
  }

  addSourceFile("FromLambda.scala",
    s"""object FromLambda {
       |  def main(args: Array[String]): Unit = {
       |    val outer = 123
       |    Array(1).foreach { x =>
       |      val localX = x
       |      Array(2).foreach { y =>
       |        val localY = y
       |        Array(3).foreach { z =>
       |          val localZ = z
       |          println() $breakpoint
       |          println()
       |        }
       |      }
       |    }
       |  }
       |}
       |""".stripMargin.trim
  )

  def testFromLambda(): Unit = expressionEvaluationTest() { implicit ctx =>
    "outer" evaluatesTo 123
    "localX" evaluatesTo 1
    "localY" evaluatesTo 2
    "localZ" evaluatesTo 3
  }

  addSourceFile("FromPartialFunction.scala",
    s"""object FromPartialFunction {
       |  def main(args: Array[String]): Unit = {
       |    val outer = 123
       |    Array(1).collect { case x =>
       |      val localX = x
       |      Array(2).collect { case y =>
       |        val localY = y
       |        Array(3).collect { case z =>
       |          val localZ = z
       |          println() $breakpoint
       |          println()
       |        }
       |      }
       |    }
       |  }
       |}
       |""".stripMargin.trim
  )

  def testFromPartialFunction(): Unit = expressionEvaluationTest() { implicit ctx =>
    "outer" evaluatesTo 123
    "localX" evaluatesTo 1
    "localY" evaluatesTo 2
    "localZ" evaluatesTo 3
  }

  addSourceFile("InObjectConstructor.scala",
    s"""object InObjectConstructor {
       |  {
       |    val x = 123
       |    println(x) $breakpoint
       |  }
       |
       |  def main(args: Array[String]): Unit = {}
       |}
       |""".stripMargin.trim
  )

  def testInObjectConstructor(): Unit = expressionEvaluationTest() { implicit ctx =>
    "x" evaluatesTo 123
  }

  addSourceFile("InClassConstructorBlock.scala",
    s"""class InClassConstructorBlock {
       |  {
       |    val x = 123
       |    println(x) $breakpoint
       |  }
       |}
       |
       |object InClassConstructorBlock {
       |  def main(args: Array[String]): Unit = {
       |    new InClassConstructorBlock()
       |  }
       |}
       |""".stripMargin.trim
  )

  def testInClassConstructorBlock(): Unit = expressionEvaluationTest() { implicit ctx =>
    "x" evaluatesTo 123
  }

  addSourceFile("InClassLevelValueDefinition.scala",
    s"""object InClassLevelValueDefinition {
       |  val outer: Int = {
       |    val inner = 123
       |    inner + 1 $breakpoint
       |  }
       |
       |  def main(args: Array[String]): Unit = {}
       |}
       |""".stripMargin.trim
  )

  def testInClassLevelValueDefinition(): Unit = expressionEvaluationTest() { implicit ctx =>
    "inner" evaluatesTo 123
  }

  addSourceFile("InOperatorBlock.scala",
    s"""object InOperatorBlock {
       |  def main(args: Array[String]): Unit = {
       |    val outer = "abc"
       |    val res = false || {
       |      val x = 123
       |      println(x) $breakpoint
       |      true
       |    }
       |    println(res)
       |  }
       |}
       |""".stripMargin.trim
  )

  def testInOperatorBlock(): Unit = expressionEvaluationTest() { implicit ctx =>
    "outer" evaluatesTo "abc"
    "x" evaluatesTo 123
  }

  addSourceFile("InLocalFunction.scala",
    s"""object InLocalFunction {
       |  def main(args: Array[String]): Unit = {
       |    val outer = 123
       |
       |    def first(): Unit = {
       |      val localX = 1
       |
       |      def second(): Unit = {
       |        val localY = 2
       |
       |        def third(): Unit = {
       |          val localZ = 3
       |          println(outer) $breakpoint
       |        }
       |
       |        third()
       |      }
       |
       |      second()
       |    }
       |
       |
       |    first()
       |  }
       |}
       |""".stripMargin.trim
  )

  def testInLocalFunction(): Unit = expressionEvaluationTest() { implicit ctx =>
    "outer" evaluatesTo 123
    "localX" evaluatesTo 1
    "localY" evaluatesTo 2
    "localZ" evaluatesTo 3
  }

  addSourceFile("InNestedValueDefinitions.scala",
    s"""object InNestedValueDefinitions {
       |  def main(args: Array[String]): Unit = {
       |    val outer = 123
       |
       |    val (x, y) = {
       |      val localX = 1
       |
       |      val z = {
       |        val localY = 2
       |        println() $breakpoint
       |        "abc"
       |      }
       |
       |      (localX, z)
       |    }
       |  }
       |}
       |""".stripMargin.trim
  )

  def testInNestedValueDefinitions(): Unit = expressionEvaluationTest() { implicit ctx =>
    "outer" evaluatesTo 123
    "localX" evaluatesTo 1
    "localY" evaluatesTo 2
  }

  addSourceFile("InSymbolicOperator.scala",
    s"""object InSymbolicOperator {
       |  def ++(): Unit = {
       |    val x = 123
       |    println(x) $breakpoint
       |  }
       |
       |  def main(args: Array[String]): Unit = {
       |    ++()
       |  }
       |}
       |""".stripMargin.trim
  )

  def testInSymbolicOperator(): Unit = expressionEvaluationTest() { implicit ctx =>
    "x" evaluatesTo 123
  }

  addSourceFile("InLocalSymbolicOperator.scala",
    s"""object InLocalSymbolicOperator {
       |  def main(args: Array[String]): Unit = {
       |    def ++(): Unit = {
       |      val x = 123
       |      println(x) $breakpoint
       |    }
       |
       |    ++()
       |  }
       |}
       |""".stripMargin.trim
  )

  def testInLocalSymbolicOperator(): Unit = expressionEvaluationTest() { implicit ctx =>
    "x" evaluatesTo 123
  }
}
