package org.jetbrains.plugins.scala.debugger.renderers

import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithScalaVersions, TestScalaVersion}
import org.junit.runner.RunWith

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_11,
  TestScalaVersion.Scala_2_12,
  TestScalaVersion.Scala_2_13,
  TestScalaVersion.Scala_3_Latest,
  TestScalaVersion.Scala_3_Latest_RC
))
class ScalaStringBuilderRendererTest extends RendererTestBase {

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
