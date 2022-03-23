package org.jetbrains.plugins.scala
package debugger.renderers

import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithScalaVersions, TestScalaVersion}
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith

import scala.concurrent.duration._

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_12,
  TestScalaVersion.Scala_2_13,
  TestScalaVersion.Scala_3_0,
  TestScalaVersion.Scala_3_1
))
@Category(Array(classOf[DebuggerTests]))
class ScalaRuntimeRefRendererTest extends RendererTestBase {

  addFileWithBreakpoints("IntRef.scala",
    s"""object IntRef {
       |  def main(args: Array[String]): Unit = {
       |    var n = 0
       |    for (_ <- 1 to 100) {
       |      n += 1$bp
       |    }
       |  }
       |}""".stripMargin)

  def testIntRef(): Unit = {
    testRuntimeRef("n", "Int", "0")
  }

  addFileWithBreakpoints("VolatileIntRef.scala",
    s"""object VolatileIntRef {
       |  def main(args: Array[String]): Unit = {
       |    @volatile var n = 0
       |    for (_ <- 1 to 100) {
       |      n += 1$bp
       |    }
       |  }
       |}""".stripMargin)

  def testVolatileIntRef(): Unit = {
    testRuntimeRef("n", "volatile Int", "0")
  }

  addFileWithBreakpoints("ObjectRef.scala",
    s"""object ObjectRef {
       |  def main(args: Array[String]): Unit = {
       |    var n = "abc"
       |    for (_ <- 1 to 100) {
       |      n = "def"$bp
       |    }
       |  }
       |}""".stripMargin)

  def testObjectRef(): Unit = {
    testRuntimeRef("n", "Object", """"abc"""")
  }

  addFileWithBreakpoints("VolatileObjectRef.scala",
    s"""object VolatileObjectRef {
       |  def main(args: Array[String]): Unit = {
       |    @volatile var n = "abc"
       |    for (_ <- 1 to 100) {
       |      n = "def"$bp
       |    }
       |  }
       |}""".stripMargin)

  def testVolatileObjectRef(): Unit = {
    testRuntimeRef("n", "volatile Object", """"abc"""")
  }

  private def testRuntimeRef(varName: String, refType: String, afterTypeLabel: String): Unit = {
    import org.junit.Assert.assertTrue
    runDebugger() {
      waitForBreakpoint()
      val (label, _) =
        renderLabelAndChildren(varName, None, parameter(0))

      val expectedLabel = s"{unwrapped Scala runtime $refType reference}$afterTypeLabel"

      assertTrue(label.contains(expectedLabel))
    }
  }
}
