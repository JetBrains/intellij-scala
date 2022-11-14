package org.jetbrains.plugins.scala.annotator

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithScalaVersions, TestScalaVersion}
import org.junit.runner.RunWith

class ScopeAnnotatorHeavyTest extends ScalaHighlightingTestBase {

  override def annotate(element: PsiElement)
                       (implicit holder: ScalaAnnotationHolder): Unit =
    ScopeAnnotator.annotateScope(element)

  def testNoTypeErasureForArray(): Unit = {
    assertMatches(errorsFromScalaCode(
      """
        |class Foo
        |class Bar
        |trait Box[T]
        |
        |object Test {
        |  def f(a: Array[Foo]) {}
        |  def f(a: Array[Bar]) {}
        |
        |  def f1(a: Array[Array[Foo]]) {}
        |  def f1(a: Array[Array[Bar]]) {}
        |
        |  def f2(a: Array[Foo], b: Array[Bar]) {}
        |  def f2(a: Array[Bar], b: Array[Foo]) {}
        |
        |  def clash(a: Array[Box[Foo]]) {}
        |  def clash(a: Array[Box[Bar]]) {}
        |}
      """.stripMargin)) {

      case Error("clash", _) :: Error("clash", _) :: Nil =>
    }
  }

  //see testdata/scalacTests/pos/t8219b.scala
  def testNoErasureInStructuralType(): Unit = assertNothing(
    errorsFromScalaCode(
      """trait Foo[T]
        |object Test {
        |
        |  type T = {
        |    def foo(f: Foo[Int])
        |    def foo(f: Foo[Boolean])
        |    def foo(f: Array[Int])
        |    def foo(f: Array[AnyRef])
        |    def foo(f: Array[Any])
        |    def foo(f: AnyVal)
        |    def foo(f: Any)
        |    def foo(f: AnyRef)
        |  }
        |}
      """.stripMargin)
  )
  //SCL-3137
  def testSCL3137(): Unit = assertNothing(
    errorsFromScalaCode(
      """
        |package a {
        |  class Foo
        |}
        |
        |package b {
        |  class Foo
        |}
        |
        |package c {
        |  trait Bar {
        |    def foo(foo: a.Foo) {}
        |    def foo(foo: b.Foo) {}
        |  }
        |}
      """.stripMargin)
  )
}

@RunWithScalaVersions(Array(TestScalaVersion.Scala_3_Latest))
@RunWith(classOf[MultipleScalaVersionsRunner])
class ScopeAnnotatorHeavyTest_Scala_3 extends ScopeAnnotatorHeavyTest {
  def testExtensionMethodsWithSameName(): Unit = assertNothing(
    errorsFromScalaCode(
      """extension (n: Int)
        |  def mySpecialToString: String = n.toString
        |  def mySpecialMkString(prefix: String, separator: String, postfix: String): String =
        |    List(n).mkString(prefix, separator, postfix)
        |
        |extension (n: Long)
        |  def mySpecialToString: String = n.toString
        |  def mySpecialMkString(prefix: String, separator: String, postfix: String): String =
        |    List(n).mkString(prefix, separator, postfix)
        """.stripMargin)
  )
}
