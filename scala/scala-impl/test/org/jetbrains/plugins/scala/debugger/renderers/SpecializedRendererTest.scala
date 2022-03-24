package org.jetbrains.plugins.scala
package debugger
package renderers

import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithScalaVersions, TestScalaVersion}
import org.junit.Assert
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_12,
  TestScalaVersion.Scala_2_13,
  TestScalaVersion.Scala_3_0,
  TestScalaVersion.Scala_3_1
))
@Category(Array(classOf[DebuggerTests]))
class SpecializedRendererTest extends RendererTestBase {

  addFileWithBreakpoints("SpecializedTuple.scala",
    s"""object SpecializedTuple {
       |  def main(args: Array[String]): Unit = {
       |    val x = (1, 2)
       |    println()$bp
       |  }
       |}
  """.stripMargin)

  def testSpecializedTuple(): Unit = {
    checkChildrenNames("x", List("_1", "_2"))
  }

  private def checkChildrenNames(varName: String, childrenNames: Seq[String]): Unit = {
    runDebugger() {
      waitForBreakpoint()
      val (_, labels) = renderLabelAndChildren(varName, Some(childrenNames.length))
      val names = labels.flatMap(_.split(" = ").headOption)
      Assert.assertEquals(childrenNames.sorted, names.sorted)
    }
  }
}
