package org.jetbrains.plugins.scala
package debugger.renderers

import org.jetbrains.plugins.scala.util.runners._
import org.junit.runner.RunWith

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_11,
  TestScalaVersion.Scala_2_12,
  TestScalaVersion.Scala_2_13,
  TestScalaVersion.Scala_3_Latest,
  TestScalaVersion.Scala_3_Latest_RC
))
class SimpleRendererTest extends RendererTestBase {
  protected def checkLabelRendering(variableToExpectedLabel: (String, String)*): Unit = {
    rendererTest() { implicit ctx =>
      variableToExpectedLabel.foreach { case (variable, expected) =>
        val (label, _) = renderLabelAndChildren(variable, renderChildren = false)
        assertEquals(expected, label)
      }
    }
  }

  addSourceFile("LiteralRendering.scala",
    s"""object LiteralRendering {
       |  def main(args: Array[String]) : Unit = {
       |    val x1: String = "42"
       |    val x2: String = null
       |    val x3: Int = 42
       |    val x4: Boolean = true
       |    println() $breakpoint
       |  }
       |}""".stripMargin)

  def testLiteralRendering(): Unit = {
    checkLabelRendering(
      "x1" -> "x1 = 42",
      "x2" -> "x2 = null",
      "x3" -> "x3 = 42",
      "x4" -> "x4 = true",
    )
  }
}
