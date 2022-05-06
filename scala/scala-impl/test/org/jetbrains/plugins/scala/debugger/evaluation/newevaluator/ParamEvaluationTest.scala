package org.jetbrains.plugins.scala.debugger.evaluation
package newevaluator

import org.jetbrains.plugins.scala._
import org.junit.experimental.categories.Category

@Category(Array(classOf[DebuggerTests]))
class ParamEvaluationTest_2_11 extends ParamEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_11
}

@Category(Array(classOf[DebuggerTests]))
class ParamEvaluationTest_2_12 extends ParamEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_12
}

@Category(Array(classOf[DebuggerTests]))
class ParamEvaluationTest_2_13 extends ParamEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_13
}

@Category(Array(classOf[DebuggerTests]))
class ParamEvaluationTest_3_0 extends ParamEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_3_0
}

@Category(Array(classOf[DebuggerTests]))
class ParamEvaluationTest_3_1 extends ParamEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_3_1
}

abstract class ParamEvaluationTestBase extends ExpressionEvaluationTestBase {
  addSourceFile("InMethod.scala",
    s"""object InMethod {
       |  def foo(x: Int, y: Int, z: Int): Int =
       |    x + y + z $breakpoint
       |
       |  def main(args: Array[String]): Unit = {
       |    foo(1, 2, 3)
       |  }
       |}
     """.stripMargin.trim
  )

  def testInMethod(): Unit = expressionEvaluationTest() { implicit ctx =>
    "x" evaluatesTo 1
    "y" evaluatesTo 2
    "z" evaluatesTo 3
  }

  addSourceFile("InLambda.scala",
    s"""object InLambda {
       |  def main(args: Array[String]): Unit = {
       |    val x = 123
       |    val y = "456"
       |    val z = 789.0
       |
       |    Array(1).foreach { n =>
       |      println(x) $breakpoint
       |      println(y)
       |      println(z)
       |    }
       |  }
       |}
     """.stripMargin.trim
  )

  def testInLambda(): Unit = expressionEvaluationTest() { implicit ctx =>
    "n" evaluatesTo 1
    "x" evaluatesTo 123
    "y" evaluatesTo "456"
    "z" evaluatesTo 789.0
  }

  addSourceFile("InLambdaMultipleParams.scala",
    s"""object InLambdaMultipleParams {
       |  def main(args: Array[String]): Unit = {
       |    val x = 123
       |    val y = "456"
       |    val z = 789.0
       |
       |    Array(1).foldLeft(0) { (a, b) =>
       |      println(x) $breakpoint
       |      println(y)
       |      println(z)
       |      a + b
       |    }
       |  }
       |}
     """.stripMargin.trim
  )

  def testInLambdaMultipleParams(): Unit = expressionEvaluationTest() { implicit ctx =>
    "a" evaluatesTo 0
    "b" evaluatesTo 1
    "x" evaluatesTo 123
    "y" evaluatesTo "456"
    "z" evaluatesTo 789.0
  }

  addSourceFile("ClassParamsInConstructor.scala",
    s"""class ClassParamsInConstructor(one: Int, two: Int) {
       |  println() $breakpoint
       |}
       |
       |object ClassParamsInConstructor {
       |  def main(args: Array[String]): Unit = {
       |    new ClassParamsInConstructor(1, 2)
       |  }
       |}
     """.stripMargin.trim
  )

  def testClassParamsInConstructor(): Unit = expressionEvaluationTest() { implicit ctx =>
    "one" evaluatesTo 1
    "two" evaluatesTo 2
  }

  addSourceFile("NestedLambdas.scala",
    s"""object NestedLambdas {
       |  def main(args: Array[String]): Unit = {
       |    Array(1).flatMap { x =>
       |      Array(2).flatMap { y =>
       |        Array(3).map { x =>
       |          println() $breakpoint
       |          x + y
       |        }
       |      }
       |    }
       |  }
       |}
     """.stripMargin.trim
  )

  def testNestedLambdas(): Unit = expressionEvaluationTest() { implicit ctx =>
    "x" evaluatesTo 3
    "y" evaluatesTo 2
  }

  addSourceFile("LambdaShadowing.scala",
    s"""object LambdaShadowing {
       |  def main(args: Array[String]): Unit = {
       |    val x = 123
       |
       |    Array(1).foreach { x =>
       |      println(x) $breakpoint
       |      println()
       |    }
       |  }
       |}
     """.stripMargin.trim
  )

  def testLambdaShadowing(): Unit = expressionEvaluationTest() { implicit ctx =>
    "x" evaluatesTo 1
  }

  addSourceFile("MethodCallsSameParamName.scala",
    s"""object MethodCallsSameParamName {
       |  def one(x: Int): Unit =
       |    two(x + 1) $breakpoint
       |  def two(x: Int): Unit =
       |    three(x + 1) $breakpoint
       |  def three(x: Int): Unit =
       |    println(x) $breakpoint
       |
       |  def main(args: Array[String]): Unit = {
       |    val x = 0
       |    one(x + 1) $breakpoint
       |  }
       |}
     """.stripMargin.trim
  )

  def testMethodCallsSameParamName(): Unit = expressionEvaluationTest()(
    { implicit ctx => "x" evaluatesTo 0 },
    { implicit ctx => "x" evaluatesTo 1 },
    { implicit ctx => "x" evaluatesTo 2 },
    { implicit ctx => "x" evaluatesTo 3 }
  )
}
