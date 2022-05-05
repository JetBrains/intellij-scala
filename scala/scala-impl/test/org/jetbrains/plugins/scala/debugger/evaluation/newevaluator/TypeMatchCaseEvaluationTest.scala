package org.jetbrains.plugins.scala.debugger.evaluation
package newevaluator

import org.jetbrains.plugins.scala._
import org.junit.experimental.categories.Category

@Category(Array(classOf[DebuggerTests]))
class TypeMatchCaseEvaluationTest_2_11 extends TypeMatchCaseEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_11
}

@Category(Array(classOf[DebuggerTests]))
class TypeMatchCaseEvaluationTest_2_12 extends TypeMatchCaseEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_12
}

@Category(Array(classOf[DebuggerTests]))
class TypeMatchCaseEvaluationTest_2_13 extends TypeMatchCaseEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_13
}

@Category(Array(classOf[DebuggerTests]))
class TypeMatchCaseEvaluationTest_3_0 extends TypeMatchCaseEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_3_0
}

@Category(Array(classOf[DebuggerTests]))
class TypeMatchCaseEvaluationTest_3_1 extends TypeMatchCaseEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_3_1
}

abstract class TypeMatchCaseEvaluationTestBase extends ExpressionEvaluationTestBase {
  addSourceFile("TypeMatchCase.scala",
    s"""object TypeMatchCase {
       |  def main(args: Array[String]): Unit = {
       |    val string: AnyRef = "string"
       |
       |    string match {
       |      case s: String =>
       |        println(s) $breakpoint
       |      case o =>
       |        println(o)
       |    }
       |  }
       |}
     """.stripMargin.trim
  )

  def testTypeMatchCase(): Unit = expressionEvaluationTest() { implicit ctx =>
    "string" evaluatesTo "string"
    "s" evaluatesTo "string"
  }
}
