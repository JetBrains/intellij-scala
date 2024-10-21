package org.jetbrains.plugins.scala
package debugger.renderers

class SimpleRendererTest_2_11 extends SimpleRendererTestBase(ScalaVersion.Latest.Scala_2_11)

class SimpleRendererTest_2_12 extends SimpleRendererTestBase(ScalaVersion.Latest.Scala_2_12)

class SimpleRendererTest_2_13 extends SimpleRendererTestBase(ScalaVersion.Latest.Scala_2_13)

class SimpleRendererTest_3_3 extends SimpleRendererTestBase(ScalaVersion.Latest.Scala_3_3)

class SimpleRendererTest_3_4 extends SimpleRendererTestBase(ScalaVersion.Latest.Scala_3_4)

class SimpleRendererTest_3_5 extends SimpleRendererTestBase(ScalaVersion.Latest.Scala_3_5)

class SimpleRendererTest_3_6 extends SimpleRendererTestBase(ScalaVersion.Latest.Scala_3_6)

class SimpleRendererTest_3_LTS_RC extends SimpleRendererTestBase(ScalaVersion.Latest.Scala_3_LTS_RC)

class SimpleRendererTest_3_Next_RC extends SimpleRendererTestBase(ScalaVersion.Latest.Scala_3_Next_RC)

abstract class SimpleRendererTestBase(scalaVersion: ScalaVersion) extends RendererTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == scalaVersion

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
