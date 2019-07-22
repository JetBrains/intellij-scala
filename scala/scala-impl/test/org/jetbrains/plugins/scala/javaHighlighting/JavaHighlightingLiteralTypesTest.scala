package org.jetbrains.plugins.scala.javaHighlighting
import org.jetbrains.plugins.scala.{ScalaVersion, Scala_2_13}

class JavaHighlightingLiteralTypesTest extends JavaHighlightingTestBase {
  override protected def supportedIn(version: ScalaVersion) = version >= Scala_2_13

  def testSCL15897(): Unit = {
    val scala =
      """
        |object Test {
        |  def a(): 123 = 123
        |}
        |""".stripMargin

    val java =
      """
        |public class JavaTest {
        |  public static void main(String[] args) {
        |    int a = Test.a();
        |  }
        |}
        |""".stripMargin
      assertNothing(errorsFromJavaCode(scala, java, "JavaTest"))
  }
}
