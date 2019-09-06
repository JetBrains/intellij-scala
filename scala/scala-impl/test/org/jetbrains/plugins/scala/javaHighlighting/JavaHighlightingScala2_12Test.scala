package org.jetbrains.plugins.scala.javaHighlighting

import org.jetbrains.plugins.scala.debugger.{ScalaVersion, Scala_2_12}
import org.jetbrains.plugins.scala.annotator.Error

/**
  * @author Alefas
  * @since 22/12/2016
  */
class JavaHighlightingScala2_12Test extends JavaHighlightingTestBase {

  override implicit val version: ScalaVersion = Scala_2_12

  def testSCL11016(): Unit = {
    val java =
      """
        |package u;
        |class A {}
        |class B extends A {}
        |abstract class C<T extends A> {
        |    abstract void foo(T t);
        |}
        |class D {
        |    public static void foo(C<? super B> c) {}
        |}
      """.stripMargin

    val scala =
      """
        |package u
        |
        |object S {
        |  def f(b: B): Unit = { }
        |  D.foo(x => f(x))
        |}
      """.stripMargin

    assertNothing(errorsFromScalaCode(scala, java))
  }

  def testSCL15021(): Unit = {
    val java =
      """
        |public class Test {
        |  public static void main(String[] args) {
        |    String s = A.methodOnCompanion();
        |  }
        |}
      """.stripMargin

    val scala =
      """
        |trait A
        |object A {
        |  def methodOnCompanion(): String = ???
        |}
      """.stripMargin

    assertNothing(errorsFromJavaCode(scala, java, "Test"))
  }

  def testSCL15936(): Unit = {
    val java =
      """
        |import java.util.Map;
        |
        |public class Api {
        |    public static void foo(Map<String, Object> params) {}
        |}
        |""".stripMargin

    val scala =
      """
        |class App {
        |  Api.foo(new java.util.TreeMap[String, Object]())
        |  Api.foo(new java.util.TreeMap[String, Any]())
        |}
        |""".stripMargin

    assertMessages(errorsFromScalaCode(scala, java))(
      Error(
        "new java.util.TreeMap[String, Any]()",
        "Type mismatch, expected: util.Map[String, AnyRef], actual: util.TreeMap[String, Any]"
      )
    )
  }
}
