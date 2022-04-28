package org.jetbrains.plugins.scala.lang.resolve

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class InlineDeclFromJavaResolveTest extends SimpleResolveTestBase {

  import SimpleResolveTestBase._

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_3_0

  def testResolveNormal(): Unit = {
    myFixture.addFileToProject(
      """
        |class InlineContainer {
        |  def foo: Int = 123
        |  inline def fooInlined: Int = 123
        |}
        |""".stripMargin,
      "hasInline.scala"
    )

    doResolveTest(
      s"""
        |public class JavaClass {
        |  public static void main(String[] args) {
        |    InlineContainer ic = new InlineContainer();
        |    ic.f${REFSRC}oo();
        |    ic.inlinedFoo();
        |  }
        |}
        |""".stripMargin,
      "JavaClass.java"
    )

    testNoResolve(
      s"""
         |public class JavaClass2 {
         |  public static void main(String[] args) {
         |    InlineContainer ic = new InlineContainer();
         |    ic.in${REFSRC}linedFoo();
         |  }
         |}
         |""".stripMargin,
      "JavaClass2.java"
    )
  }
}
