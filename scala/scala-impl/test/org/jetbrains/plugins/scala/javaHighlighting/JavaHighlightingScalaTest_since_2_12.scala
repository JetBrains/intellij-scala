package org.jetbrains.plugins.scala.javaHighlighting

import org.jetbrains.plugins.scala.DependencyManagerBase._
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.annotator.Message
import org.jetbrains.plugins.scala.base.libraryLoaders.{IvyManagedLoader, LibraryLoader}
import org.jetbrains.plugins.scala.javaHighlighting.JavaHighlightingScalaTest_since_2_12.addScalaPackageObjectDefinitions

class JavaHighlightingScalaTest_since_2_12 extends JavaHighlightingTestBase {

  import Message._

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= ScalaVersion.Latest.Scala_2_12

  override protected def librariesLoaders: Seq[LibraryLoader] = {
    super.librariesLoaders ++ Seq(
      IvyManagedLoader(("org.jetbrains.kotlin" % "kotlin-stdlib" % "1.9.22").transitive())
    )
  }

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

    addDummyJavaFile(java)
    assertNothing(errorsFromScalaCode(scala))
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

    addDummyJavaFile(java)
    assertMessages(errorsFromScalaCode(scala))(
      Error(
        "new java.util.TreeMap[String, Any]()",
        "Type mismatch, expected: util.Map[String, AnyRef], actual: util.TreeMap[String, Any]"
      )
    )
  }

  def testUseDeclarationsDefinedInScalaPackageObject(): Unit = {
    addScalaPackageObjectDefinitions(myFixture.addFileToProject _)

    assertNothing(errorsFromJavaCode(
      """public class JavaMain {
        |    public static void main(String[] args) {
        |        //package object
        |        System.out.println(org.non_legacy.package$.MODULE$.fooStringType());
        |        System.out.println(org.non_legacy.package$.MODULE$.fooInnerClassType());
        |
        |        //legacy package object (used before 2.8 but is still supported in the latest scala versions)
        |        System.out.println(org.legacy.package$.MODULE$.fooStringType());
        |        System.out.println(org.legacy.package$.MODULE$.fooInnerClassType());
        |
        |        //ordinary object
        |        System.out.println(org.OrdinaryObject.fooStringType());
        |        System.out.println(org.OrdinaryObject.fooInnerClassType());
        |        System.out.println(org.OrdinaryObject$.MODULE$.fooStringType());
        |        System.out.println(org.OrdinaryObject$.MODULE$.fooInnerClassType());
        |    }
        |}
        |""".stripMargin,
      "JavaMain"
    ))
  }

  def testUseDeclarationsDefinedInScalaPackageObject_FromScalaCode(): Unit = {
    addScalaPackageObjectDefinitions(myFixture.addFileToProject _)

    assertNoErrors(
      """//noinspection ScalaUnusedExpression
        |class ScalaMain {
        |  //Scala-way usage
        |  org.legacy.fooStringType
        |  //org.legacy.`package`.fooStringType //(minor) is supported by scalac but we don't support it
        |  null : org.legacy.MyClassInPackageObject
        |  null : org.legacy.`package`.MyClassInPackageObject
        |
        |  org.non_legacy.fooStringType
        |  //org.non_legacy.`package`.fooStringType //(minor) is supported by scalac but we don't support it
        |  null : org.legacy.MyClassInPackageObject
        |  null : org.legacy.`package`.MyClassInPackageObject
        |}
        |""".stripMargin
    )
  }
}

object JavaHighlightingScalaTest_since_2_12 {

  def addScalaPackageObjectDefinitions(addFileToProject: (String, String) => Unit): Unit = {
    val ScalaPackageObjectDefinition_Legacy =
      """package org.legacy
        |
        |//Legacy way to define a package object (prior Scala 2.8)
        |object `package` {
        |  class MyClassInPackageObject
        |
        |  def fooStringType: String = null
        |  def fooInnerClassType: MyClassInPackageObject = null
        |}
        |""".stripMargin

    val ScalaPackageObjectDefinition_NonLegacy =
      """package org
        |
        |package object non_legacy {
        |  class MyClassInPackageObject
        |  def fooStringType: String = null
        |  def fooInnerClassType: MyClassInPackageObject = null
        |}
        |""".stripMargin

    val ScalaObjectDefinition =
      """package org
        |
        |object OrdinaryObject {
        |  class MyClassInObject
        |  def fooStringType: String = null
        |  def fooInnerClassType: MyClassInObject = null
        |}
        |""".stripMargin

    addFileToProject("org/legacy/package.scala", ScalaPackageObjectDefinition_Legacy)
    addFileToProject("org/non_legacy/package.scala", ScalaPackageObjectDefinition_NonLegacy)
    addFileToProject("org/OrdinaryObject.scala", ScalaObjectDefinition)
  }
}
