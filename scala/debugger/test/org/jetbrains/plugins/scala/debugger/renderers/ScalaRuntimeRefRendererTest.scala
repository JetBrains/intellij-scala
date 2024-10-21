package org.jetbrains.plugins.scala
package debugger.renderers

class ScalaRuntimeRefRendererTest_2_12 extends ScalaRuntimeRefRendererTestBase(ScalaVersion.Latest.Scala_2_12)

class ScalaRuntimeRefRendererTest_2_13 extends ScalaRuntimeRefRendererTestBase(ScalaVersion.Latest.Scala_2_13)

class ScalaRuntimeRefRendererTest_3_3 extends ScalaRuntimeRefRendererTestBase(ScalaVersion.Latest.Scala_3_3)

class ScalaRuntimeRefRendererTest_3_4 extends ScalaRuntimeRefRendererTestBase(ScalaVersion.Latest.Scala_3_4)

class ScalaRuntimeRefRendererTest_3_5 extends ScalaRuntimeRefRendererTestBase(ScalaVersion.Latest.Scala_3_5)

class ScalaRuntimeRefRendererTest_3_6 extends ScalaRuntimeRefRendererTestBase(ScalaVersion.Latest.Scala_3_6)

class ScalaRuntimeRefRendererTest_3_LTS_RC extends ScalaRuntimeRefRendererTestBase(ScalaVersion.Latest.Scala_3_LTS_RC)

class ScalaRuntimeRefRendererTest_3_Next_RC extends ScalaRuntimeRefRendererTestBase(ScalaVersion.Latest.Scala_3_Next_RC)

abstract class ScalaRuntimeRefRendererTestBase(scalaVersion: ScalaVersion) extends RendererTestBase {
  
  override protected def supportedIn(version: ScalaVersion): Boolean = version == scalaVersion

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
