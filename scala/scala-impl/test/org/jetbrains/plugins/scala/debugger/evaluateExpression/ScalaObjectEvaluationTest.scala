package org.jetbrains.plugins.scala
package debugger
package evaluateExpression

import org.junit.experimental.categories.Category

@Category(Array(classOf[DebuggerTests]))
class ScalaObjectEvaluationTest_2_11 extends ScalaObjectEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion) = version == LatestScalaVersions.Scala_2_11
}

@Category(Array(classOf[DebuggerTests]))
class ScalaObjectEvaluationTest_2_12 extends ScalaObjectEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion) =
    version >= LatestScalaVersions.Scala_2_12 && version <= LatestScalaVersions.Scala_2_13
}
@Category(Array(classOf[DebuggerTests]))
class ScalaObjectEvaluationTest_3_0 extends ScalaObjectEvaluationTestBase {
  override protected def supportedIn(version: ScalaVersion) = version  >= LatestScalaVersions.Scala_3_0

  override def testEvaluateObjects(): Unit = failing(super.testEvaluateObjects())

  override def testInnerClassObjectFromObject(): Unit = failing(super.testInnerClassObjectFromObject())
}

@Category(Array(classOf[DebuggerTests]))
abstract class ScalaObjectEvaluationTestBase extends ScalaDebuggerTestCase {
  addFileWithBreakpoints("SimpleObject.scala",
    s"""
       |object EvaluateObjects {
       |  def main(args: Array[String]): Unit = {
       |    println()$bp
       |  }
       |}
       """.stripMargin.trim()
  )
  addSourceFile("Simple.scala", "object Simple")
  addSourceFile("qual/Simple.scala",
    s"""
      |package qual
      |
      |object Simple
      """.stripMargin.trim()
  )
  addSourceFile("qual/SimpleCaseClass.scala",
    s"""
       |package qual
       |
       |case class SimpleCaseClass()
      """.stripMargin.trim()
  )
  addSourceFile("StableInner.scala",
    s"""
       |package qual
       |
       |object StableInner {
       |  object Inner
       |}
      """.stripMargin.trim()
  )
  addSourceFile("qual/ClassInner.scala",
    s"""
       |package qual
       |
       |class ClassInner {
       |  object Inner
       |}
      """.stripMargin.trim()
  )
  def testEvaluateObjects(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalStartsWith("Simple", "Simple$")
      evalStartsWith("qual.Simple", "qual.Simple$")
      evalStartsWith("collection.JavaConversions", "scala.collection.JavaConversions$")
      evalEquals("qual.SimpleCaseClass", "SimpleCaseClass")
      evalStartsWith("qual.StableInner.Inner", "qual.StableInner$Inner$")
      evalStartsWith("val x = new qual.ClassInner(); x.Inner", "qual.ClassInner$Inner$")
    }
  }

  addFileWithBreakpoints("InnerClassObjectFromObject.scala",
    s"""
       |object InnerClassObjectFromObject {
       |  class S {
       |    object SS {
       |      object S {
       |        def foo(): Unit = {
       |          SS.S //to have $$outer field
       |          println()$bp
       |        }
       |      }
       |      object G
       |    }
       |    def foo(): Unit = {
       |      SS.S.foo()
       |    }
       |  }
       |
       |  def main(args: Array[String]): Unit = {
       |    val x = new S()
       |    x.foo()
       |  }
       |}
      """.stripMargin.trim()
  )
  def testInnerClassObjectFromObject(): Unit = {
    runDebugger() {
      waitForBreakpoint()
      evalStartsWith("SS.G", "InnerClassObjectFromObject$S$SS$G")
      evalStartsWith("SS.S", "InnerClassObjectFromObject$S$SS$S")
      evalStartsWith("S", "InnerClassObjectFromObject$S$SS$S")
      evalStartsWith("SS", "InnerClassObjectFromObject$S$SS$")
    }
  }
}