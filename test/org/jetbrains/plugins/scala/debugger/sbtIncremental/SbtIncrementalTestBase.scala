package org.jetbrains.plugins.scala
package debugger
package sbtIncremental

import org.jetbrains.plugins.scala.project.IncrementalityType
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration

abstract class SbtIncrementalTestBase extends ScalaDebuggerTestBase {
  protected def addOtherLibraries() = {}

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

class SbtIncrementalTest extends SbtIncrementalTestBase with ScalaVersion_2_11
