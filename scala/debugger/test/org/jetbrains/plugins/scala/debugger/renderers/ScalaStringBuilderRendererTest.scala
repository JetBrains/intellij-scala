package org.jetbrains.plugins.scala.debugger.renderers

import org.jetbrains.plugins.scala.ScalaVersion

class ScalaStringBuilderRendererTest_2_11 extends ScalaStringBuilderRendererTestBase(ScalaVersion.Latest.Scala_2_11)

class ScalaStringBuilderRendererTest_2_12 extends ScalaStringBuilderRendererTestBase(ScalaVersion.Latest.Scala_2_12)

class ScalaStringBuilderRendererTest_2_13 extends ScalaStringBuilderRendererTestBase(ScalaVersion.Latest.Scala_2_13)

class ScalaStringBuilderRendererTest_3_3 extends ScalaStringBuilderRendererTestBase(ScalaVersion.Latest.Scala_3_3)

class ScalaStringBuilderRendererTest_3_4 extends ScalaStringBuilderRendererTestBase(ScalaVersion.Latest.Scala_3_4)

class ScalaStringBuilderRendererTest_3_5 extends ScalaStringBuilderRendererTestBase(ScalaVersion.Latest.Scala_3_5)

class ScalaStringBuilderRendererTest_3_6 extends ScalaStringBuilderRendererTestBase(ScalaVersion.Latest.Scala_3_6)

class ScalaStringBuilderRendererTest_3_LTS_RC extends ScalaStringBuilderRendererTestBase(ScalaVersion.Latest.Scala_3_LTS_RC)

class ScalaStringBuilderRendererTest_3_Next_RC extends ScalaStringBuilderRendererTestBase(ScalaVersion.Latest.Scala_3_Next_RC)

abstract class ScalaStringBuilderRendererTestBase(scalaVersion: ScalaVersion) extends RendererTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == scalaVersion

  addSourceFile("StringBuilders.scala",
    s"""object StringBuilders {
       |  def main(args: Array[String]): Unit = {
       |    val string = (1 to 10).mkString
       |
       |    val stringBuilderScala = new scala.StringBuilder(string)
       |    val stringBuilderJava = new java.lang.StringBuilder(string)
       |
       |    println() $breakpoint
       |  }
       |}""".stripMargin)

  def testStringBuilders(): Unit = {
    val expectedString = "12345678910"
    val expectedStringBuilder = s"{StringBuilder@uniqueID}$expectedString"

    rendererTest() { implicit ctx =>
      val (stringLabel, _) = renderLabelAndChildren("string", renderChildren = false)
      assertEquals(s"string = $expectedString", stringLabel)
      val (stringBuilderScalaLabel, _) = renderLabelAndChildren("stringBuilderScala", renderChildren = false)
      assertEquals(s"stringBuilderScala = $expectedStringBuilder", stringBuilderScalaLabel)
      val (stringBuilderJavaLabel, _) = renderLabelAndChildren("stringBuilderJava", renderChildren = false)
      assertEquals(s"stringBuilderJava = $expectedStringBuilder", stringBuilderJavaLabel)
    }
  }
}
