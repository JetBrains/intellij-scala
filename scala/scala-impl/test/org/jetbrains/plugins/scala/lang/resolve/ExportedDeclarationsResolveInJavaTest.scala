package org.jetbrains.plugins.scala.lang.resolve

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.jetbrains.plugins.scala.lang.resolve.SimpleResolveTestBase.REFSRC

class ExportedDeclarationsResolveInJavaTest extends SimpleResolveTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  def testSCL20281(): Unit = {
    myFixture.addFileToProject(
      "hasExports.scala",
      """
        |package foo
        |
        |object O1 {
        |  export O2.fooFromObject
        |
        |  O1.fooFromObject
        |
        |  val a = new A
        |  val b = new B
        |
        |  b.fooFromB(1)
        |  a.fooFromB(2)
        |}
        |
        |object O2 {
        |  def fooFromObject: Int = 1
        |}
        |
        |class A {
        |  private val b: B = new B()
        |  export b.fooFromB
        |}
        |class B {
        |  def fooFromB(x: Int): Int = x + 2
        |}
        |""".stripMargin
    )

    doResolveTest(
      s"""
         |package foo;
         |
         |import foo.O1$$;
         |
         |public class MyJava {
         |    public static void main(String[] args) {
         |        System.out.println(O1$$.MODULE$$.fooFrom${REFSRC}Object());
         |    }
         |}
         |""".stripMargin,
      "JavaClass.java"
    )

    doResolveTest(
      s"""
         |package foo;
         |
         |import foo.O2$$;
         |
         |public class MyJava {
         |    public static void main(String[] args) {
         |        System.out.println(O2$$.MODULE$$.fooFrom${REFSRC}Object())
         |    }
         |}
         |""".stripMargin,
      "JavaClass2.java"
    )

    doResolveTest(
      s"""
         |package foo;
         |
         |import foo.A;
         |import foo.B;
         |
         |public class MyJava {
         |    public static void main(String[] args) {
         |      A a = new A();
         |      a.foo${REFSRC}FromB(2);
         |    }
         |}
         |""".stripMargin,
      "JavaClass3.java"
    )

    doResolveTest(
      s"""
         |package foo;
         |
         |import foo.A;
         |import foo.B;
         |
         |public class MyJava {
         |    public static void main(String[] args) {
         |      B b = new B();
         |      b.fooFr${REFSRC}omB(1);
         |    }
         |}
         |""".stripMargin,
      "JavaClass4.java"
    )
  }

}
