package org.jetbrains.plugins.scala.debugger.evaluation
package newevaluator

import org.jetbrains.plugins.scala._
import org.junit.experimental.categories.Category

import java.nio.file.Path

@Category(Array(classOf[DebuggerTests]))
class ClassMemberEvaluationTest_2_11 extends ClassMemberEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_11
}

@Category(Array(classOf[DebuggerTests]))
class ClassMemberEvaluationTest_2_12 extends ClassMemberEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_12
}

@Category(Array(classOf[DebuggerTests]))
class ClassMemberEvaluationTest_2_13 extends ClassMemberEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_13
}

@Category(Array(classOf[DebuggerTests]))
class ClassMemberEvaluationTest_3_0 extends ClassMemberEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_3_0
}

@Category(Array(classOf[DebuggerTests]))
class ClassMemberEvaluationTest_3_1 extends ClassMemberEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_3_1
}

abstract class ClassMemberEvaluationTestBase extends ExpressionEvaluationTestBase {
  addSourceFile(Path.of("test", "InObject.scala").toString,
    s"""package test
       |
       |object InObject {
       |  private[this] val privateThisVal: Int = 1
       |  private val privateVal: Int = 2
       |  private[test] val packagePrivateVal: Int = 3
       |  val publicVal: Int = 4
       |
       |  private[this] var privateThisVar: Int = 5
       |  private var privateVar: Int = 6
       |  private[test] var packagePrivateVar: Int = 7
       |  var publicVar: Int = 8
       |
       |  def main(args: Array[String]): Unit = {
       |    println() $breakpoint
       |    println(privateThisVal)
       |    println(privateVal)
       |    println(privateThisVar)
       |    println(privateVar)
       |  }
       |}
     """.stripMargin.trim
  )

  def testInObject(): Unit = expressionEvaluationTest("test.InObject") { implicit ctx =>
    "privateThisVal" evaluatesTo 1
    "privateVal" evaluatesTo 2
    "packagePrivateVal" evaluatesTo 3
    "publicVal" evaluatesTo 4
    "privateThisVar" evaluatesTo 5
    "privateVar" evaluatesTo 6
    "packagePrivateVar" evaluatesTo 7
    "publicVar" evaluatesTo 8
  }

  addSourceFile(Path.of("test", "InClass.scala").toString,
    s"""package test
       |
       |class InClass {
       |  private[this] val privateThisVal: Int = 1
       |  private val privateVal: Int = 2
       |  private[test] val packagePrivateVal: Int = 3
       |  val publicVal: Int = 4
       |
       |  private[this] var privateThisVar: Int = 5
       |  private var privateVar: Int = 6
       |  private[test] var packagePrivateVar: Int = 7
       |  var publicVar: Int = 8
       |
       |  def foo(): Unit = {
       |    println() $breakpoint
       |    println(privateThisVal)
       |    println(privateVal)
       |    println(privateThisVar)
       |    println(privateVar)
       |  }
       |}
       |
       |object InClass {
       |  def main(args: Array[String]): Unit = {
       |    new InClass().foo()
       |  }
       |}
     """.stripMargin.trim
  )

  def testInClass(): Unit = expressionEvaluationTest("test.InClass") { implicit ctx =>
    "privateThisVal" evaluatesTo 1
    "privateVal" evaluatesTo 2
    "packagePrivateVal" evaluatesTo 3
    "publicVal" evaluatesTo 4
    "privateThisVar" evaluatesTo 5
    "privateVar" evaluatesTo 6
    "packagePrivateVar" evaluatesTo 7
    "publicVar" evaluatesTo 8
  }
}
