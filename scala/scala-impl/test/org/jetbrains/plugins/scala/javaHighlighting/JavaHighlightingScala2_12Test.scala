package org.jetbrains.plugins
package scala
package javaHighlighting

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
}
