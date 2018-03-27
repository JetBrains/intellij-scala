package org.jetbrains.plugins.scala
package debugger
package sbtIncremental

import org.jetbrains.plugins.scala.project.IncrementalityType
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.junit.experimental.categories.Category

@Category(Array(classOf[DebuggerTests]))
class SbtIncrementalTest extends SbtIncrementalTestBase {
  override implicit val version: ScalaVersion = Scala_2_11
}

abstract class SbtIncrementalTestBase extends ScalaDebuggerTestBase {
  protected def addOtherLibraries(): Unit = {}

  override def setUp(): Unit = {
    super.setUp()
    ScalaCompilerConfiguration.instanceIn(myProject).incrementalityType = IncrementalityType.SBT
  }

  addSourceFile("Simple.scala",
    s"""
      |object Simple {
      |  def main(args: Array[String]): Unit = {
      |  }
      |}
    """.stripMargin.trim)
  def testSimple() {
    make()
  }
}
