package org.jetbrains.plugins.scala
package debugger.renderers

import org.jetbrains.plugins.scala.util.runners._
import org.junit.runner.RunWith

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_12,
  TestScalaVersion.Scala_2_13,
  TestScalaVersion.Scala_3_Latest,
  TestScalaVersion.Scala_3_Latest_RC
))
class SpecializedRendererTest extends RendererTestBase {

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
