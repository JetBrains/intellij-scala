package org.jetbrains.plugins.scala
package debugger
package sbtIncremental

import org.jetbrains.plugins.scala.base.ScalaSdkOwner
import org.jetbrains.plugins.scala.project.IncrementalityType
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.junit.experimental.categories.Category

@Category(Array(classOf[DebuggerTests]))
class SbtIncrementalTest extends ScalaDebuggerTestBase with ScalaSdkOwner {
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
