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
}
