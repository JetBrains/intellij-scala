package org.jetbrains.plugins.scala.debugger.renderers

import org.junit.Assert.assertEquals

class SimpleRendererTest extends RendererTestBase {
  protected def checkLabelRendering(variableToExpectedLabel: (String, String)*): Unit = {
    runDebugger() {
      waitForBreakpoint()
      for {
        (variable, expected) <- variableToExpectedLabel
      } {
        val (label, _) = renderLabelAndChildren(variable, _.getLabel, renderChildren = false)
        assertEquals(expected, label)
      }
    }
  }

  addFileWithBreakpoints("LiteralRendering.scala",
    s"""
       |object LiteralRendering {
       |  def main(args: Array[String]) : Unit = {
       |    val x1: String = "42"
       |    val x2: String = null
       |    val x3: Int = 42
       |    val x4: Boolean = true
       |
       |    println() $bp
       |  }
       |}
      """.replace("\r", "").stripMargin.trim
  )
  def testLiteralRendering(): Unit = {
    checkLabelRendering(
      "x1" -> "x1 = 42",
      "x2" -> "x2 = null",
      "x3" -> "x3 = 42",
      "x4" -> "x4 = true",
    )
  }

}
