package org.jetbrains.plugins.scala
package debugger.renderers

class SpecializedRendererTest_2_12 extends SpecializedRendererTestBase(ScalaVersion.Latest.Scala_2_12)

class SpecializedRendererTest_2_13 extends SpecializedRendererTestBase(ScalaVersion.Latest.Scala_2_13)

class SpecializedRendererTest_3_3 extends SpecializedRendererTestBase(ScalaVersion.Latest.Scala_3_3)

class SpecializedRendererTest_3_4 extends SpecializedRendererTestBase(ScalaVersion.Latest.Scala_3_4)

class SpecializedRendererTest_3_5 extends SpecializedRendererTestBase(ScalaVersion.Latest.Scala_3_5)

class SpecializedRendererTest_3_6 extends SpecializedRendererTestBase(ScalaVersion.Latest.Scala_3_6)

class SpecializedRendererTest_3_LTS_RC extends SpecializedRendererTestBase(ScalaVersion.Latest.Scala_3_LTS_RC)

class SpecializedRendererTest_3_Next_RC extends SpecializedRendererTestBase(ScalaVersion.Latest.Scala_3_Next_RC)

abstract class SpecializedRendererTestBase(scalaVersion: ScalaVersion) extends RendererTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == scalaVersion

  addSourceFile("SpecializedTuple.scala",
    s"""object SpecializedTuple {
       |  def main(args: Array[String]): Unit = {
       |    val x = (1, 2)
       |    println() $breakpoint
       |  }
       |}""".stripMargin)

  def testSpecializedTuple(): Unit = {
    checkChildrenNames("x", List("_1", "_2"))
  }

  private def checkChildrenNames(varName: String, childrenNames: Seq[String]): Unit = {
    rendererTest() { implicit ctx =>
      val (_, labels) = renderLabelAndChildren(varName, renderChildren = true)
      val names = labels.flatMap(_.split(" = ").headOption)
      assertEquals(childrenNames.sorted, names.sorted)
    }
  }
}
