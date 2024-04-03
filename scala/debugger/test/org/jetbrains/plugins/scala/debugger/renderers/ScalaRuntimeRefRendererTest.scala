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
class ScalaRuntimeRefRendererTest extends RendererTestBase {

  addSourceFile("IntRef.scala",
    s"""object IntRef {
       |  def main(args: Array[String]): Unit = {
       |    var n = 0
       |    for (_ <- 1 to 1) {
       |      n += 1 $breakpoint
       |    }
       |  }
       |}""".stripMargin)

  def testIntRef(): Unit = {
    testRuntimeRef("n$1", "Int", "0")
  }

  addSourceFile("VolatileIntRef.scala",
    s"""object VolatileIntRef {
       |  def main(args: Array[String]): Unit = {
       |    @volatile var n = 0
       |    for (_ <- 1 to 1) {
       |      n += 1 $breakpoint
       |    }
       |  }
       |}""".stripMargin)

  def testVolatileIntRef(): Unit = {
    testRuntimeRef("n$1", "volatile Int", "0")
  }

  addSourceFile("ObjectRef.scala",
    s"""object ObjectRef {
       |  def main(args: Array[String]): Unit = {
       |    var n = "abc"
       |    for (_ <- 1 to 1) {
       |      n = "def" $breakpoint
       |    }
       |  }
       |}""".stripMargin)

  def testObjectRef(): Unit = {
    testRuntimeRef("n$1", "Object", """"abc"""")
  }

  addSourceFile("VolatileObjectRef.scala",
    s"""object VolatileObjectRef {
       |  def main(args: Array[String]): Unit = {
       |    @volatile var n = "abc"
       |    for (_ <- 1 to 1) {
       |      n = "def" $breakpoint
       |    }
       |  }
       |}""".stripMargin)

  def testVolatileObjectRef(): Unit = {
    testRuntimeRef("n$1", "volatile Object", """"abc"""")
  }

  private def testRuntimeRef(varName: String, refType: String, afterTypeLabel: String): Unit = {
    rendererTest() { implicit ctx =>
      val (label, _) = renderLabelAndChildren(varName, renderChildren = false)
      val expectedLabel = s"{unwrapped Scala runtime $refType reference}$afterTypeLabel"
      org.junit.Assert.assertTrue(label.contains(expectedLabel))
    }
  }
}
