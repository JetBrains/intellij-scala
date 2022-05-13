package org.jetbrains.plugins.scala
package debugger
package evaluation

import org.junit.experimental.categories.Category

@Category(Array(classOf[DebuggerTests]))
class TypedPatternInPartialFunctionEvaluationTest_2_11 extends TypedPatternInPartialFunctionEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_11
}

@Category(Array(classOf[DebuggerTests]))
class TypedPatternInPartialFunctionEvaluationTest_2_12 extends TypedPatternInPartialFunctionEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_12
}

@Category(Array(classOf[DebuggerTests]))
class TypedPatternInPartialFunctionEvaluationTest_2_13 extends TypedPatternInPartialFunctionEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_13
}

@Category(Array(classOf[DebuggerTests]))
class TypedPatternInPartialFunctionEvaluationTest_3_0 extends TypedPatternInPartialFunctionEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_3_0
}

@Category(Array(classOf[DebuggerTests]))
class TypedPatternInPartialFunctionEvaluationTest_3_1 extends TypedPatternInPartialFunctionEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_3_1
}

abstract class TypedPatternInPartialFunctionEvaluationTestBase extends ExpressionEvaluationTestBase {
  addSourceFile("PartialFunctionCase.scala",
    s"""object PartialFunctionCase {
       |  def main(args: Array[String]): Unit = {
       |    Array(1).collect {
       |      case n: Int =>
       |        n.toString $breakpoint
       |      case _ => "abc"
       |    }
       |  }
       |}
       |""".stripMargin.trim
  )

  def testPartialFunctionCase(): Unit = expressionEvaluationTest() { implicit ctx =>
    "n" evaluatesTo "1"
  }

  addSourceFile("GuardsCanBeIgnored.scala",
    s"""object GuardsCanBeIgnored {
       |  def main(args: Array[String]): Unit = {
       |    Array(1).collect {
       |      case n: Int if n == 1 =>
       |        n.toString $breakpoint
       |      case _ => "abc"
       |    }
       |  }
       |}
       |""".stripMargin.trim
  )

  def testGuardsCanBeIgnored(): Unit = expressionEvaluationTest() { implicit ctx =>
    "n" evaluatesTo "1"
  }

  addSourceFile("PatternMatchPartialFunction.scala",
    s"""object PatternMatchPartialFunction {
       |  final case class Person(name: String, age: Int)
       |
       |  def main(args: Array[String]): Unit = {
       |    Array(Person("Name", 25)).collect {
       |      case Person(name, age) =>
       |        println(s"$$name, $$age")
       |        println() $breakpoint
       |        println()
       |    }
       |  }
       |}
       |""".stripMargin.trim
  )

  def testPatternMatchPartialFunction(): Unit = expressionEvaluationTest() { implicit ctx =>
    "name" evaluatesTo "Name"
    "age" evaluatesTo 25
  }
}
