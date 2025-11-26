package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.{ScalaVersion, TypecheckerTests}
import org.jetbrains.plugins.scala.javaHighlighting.JavaHighlightingTestBase
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
class ScGenericCallAnnotatorJavaTest extends JavaHighlightingTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_3

  def testResolveJavaClass(): Unit = {
    val java =
      """public class MyJavaClass<A extends B, B, C, D> {}
        |""".stripMargin
    val scala =
      """val _ = MyJavaClass()
        |val _ = MyJavaClass[String, String, Int, Int]()
        |""".stripMargin

    addDummyJavaFile(java)
    assertNothing(errorsFromScala3Code(scala))
  }

  def testDontResolveWrongJavaClass(): Unit = {
    val java =
      """public class MyJavaClass2<A extends B, B, C, D> {}
        |""".stripMargin
    val scala =
      """def main(): Unit = {
        |  val _ = MyJavaClass()
        |  val _ = MyJavaClass[String, String, Int, Int]()
        |}
        |""".stripMargin

    addDummyJavaFile(java)
    assertErrors(scala,
      Message.Error("MyJavaClass", "Cannot resolve symbol MyJavaClass"),
      Message.Error("MyJavaClass", "Cannot resolve symbol MyJavaClass"))
  }

  def testJavaClassUpperBound(): Unit = {
    val java =
      """public class MyJavaClass<A extends B, B, C, D> {}
        |""".stripMargin
    val scala =
      """val _ = MyJavaClass()
        |val _ = MyJavaClass[Int, String, Int, Int]()
        |""".stripMargin

    addDummyJavaFile(java)
    assertErrors(scala,
      Message.Error("Int", "Type Int does not conform to upper bound String of type parameter A"))
  }

  val javaTypedConstr =
    """public class MyJavaClass<A, B, C> {
      | public <T> MyJavaClass(T i) {}
      |}
      |""".stripMargin
  def testJavaTypedConstructorSuc(): Unit = {
    val scala =
      """val _ = MyJavaClass("a")
        |val _ = MyJavaClass[String, Int, String]("a")
        |""".stripMargin

    addDummyJavaFile(javaTypedConstr)
    assertNothing(errorsFromScala3Code(scala))
  }

  def testJavaTypedConstructorFail1(): Unit = {
    val scala =
      """val _ = MyJavaClass[String]("a")
        |""".stripMargin

    addDummyJavaFile(javaTypedConstr)
    assertErrors(scala,
      Message.Error("g]", "Unspecified type parameters: B, C")
    )
  }

  def testJavaTypedConstructorFail2(): Unit = {
    val scala =
      """val _ = MyJavaClass[String, Int, String, String]("a")
        |""".stripMargin

    addDummyJavaFile(javaTypedConstr)
    assertErrors(scala,
      Message.Error(", S", "Too many type arguments for method MyJavaClass, expected: 3, found: 4")
    )
  }
}
