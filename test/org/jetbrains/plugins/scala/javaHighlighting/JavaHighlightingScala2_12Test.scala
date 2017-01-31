package org.jetbrains.plugins.scala.javaHighlighting

import org.jetbrains.plugins.scala.util.TestUtils.ScalaSdkVersion

/**
  * @author Alefas
  * @since 22/12/2016
  */
class JavaHighlightingScala2_12Test extends JavaHighlightingTestBase {

  override protected def scalaSdkVersion: ScalaSdkVersion = ScalaSdkVersion._2_12

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
}
