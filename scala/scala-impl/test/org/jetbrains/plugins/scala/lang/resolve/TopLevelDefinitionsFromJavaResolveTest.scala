package org.jetbrains.plugins.scala.lang.resolve
import org.jetbrains.plugins.scala.lang.resolve.SimpleResolveTestBase.REFSRC
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class TopLevelDefinitionsFromJavaResolveTest extends SimpleResolveTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3

  def testSCL21409(): Unit = {
    myFixture.addFileToProject(
      "hasTopLevel.scala",
      """
        |package foo
        |
        |def fooBar: Int = 123
        |val xxx = 42d
        |val (zzz, y) = (1, 2)
        |class Cls
        |
        |""".stripMargin
    )

    doResolveTest(
      s"""
         |package foo;
         |
         |import static foo.hasTopLevel$$package.fooBar;
         |
         |public class JavaClass {
         |  public static void main(String[] args) {
         |    System.out.println(foo${REFSRC}Bar());
         |  }
         |}
         |""".stripMargin,
      "JavaClass1.java"
    )

    doResolveTest(
      s"""
         |package foo;
         |
         |import static foo.hasTopLevel$$package.xxx;
         |
         |public class JavaClass {
         |  public static void main(String[] args) {
         |    System.out.println(xx${REFSRC}x());
         |  }
         |}
         |""".stripMargin,
      "JavaClass2.java"
    )

    doResolveTest(
      s"""
         |package foo;
         |
         |import static foo.hasTopLevel$$package.zzz;
         |
         |public class JavaClass {
         |  public static void main(String[] args) {
         |    System.out.println(zz${REFSRC}z());
         |  }
         |}
         |""".stripMargin,
      "JavaClass3.java"
    )

    testNoResolve(
      s"""
         |package foo;
         |
         |import static foo.hasTopLevel$$package.Cls;
         |
         |public class JavaClass {
         |  public static void main(String[] args) {
         |    Cls cls = new C${REFSRC}ls();
         |  }
         |}
         |""".stripMargin,
      "JavaClass4.java"
    )
  }
}
