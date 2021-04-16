package org.jetbrains.plugins.scala.failed.resolve

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.annotator.AnnotatorHolderMock
import org.jetbrains.plugins.scala.annotator.element.ScReferenceAnnotator
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.extensions.{IteratorExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference

/**
  * @author Roman.Shein
  * @since 25.03.2016.
  */
class ArgumentTypeMismatchTest extends SimpleTestCase {
  override protected def shouldPass: Boolean = false

  def testSCL4687(): Unit = {
    val code =
      """
        |object A {
        |  class Z[T] {
        |    def m(t: T): T = t
        |  }
        |
        |  def foo[T]: Z[T] = null.asInstanceOf[Z[T]]
        |
        |  def goo[G](z: Z[G]): Z[G] = z
        |
        |  goo(foo).m(1)
        |}
      """.stripMargin
    assertNothing(messages(code))
  }

  def testSCL9686(): Unit = assertNothing(
    messages {
      """
        |class Scl9686 {
        |  class A {
        |    def foo(a: Int = 1): Unit = {}
        |  }
        |
        |  class B extends A {
        |    override def foo(a: Int): Unit = {}
        |  }
        |
        |  class C extends B {
        |    override def foo(a: Int): Unit = {}
        |  }
        |
        |  class D extends C {
        |    override def foo(a: Int): Unit = {}
        |  }
        |
        |  object Some {
        |    def main(args: Array[String]) {
        |      (new B()).foo()
        |      (new C()).foo() // Error: Cannot resolve reference foo() with such signature
        |      (new D()).foo() // Error: Cannot resolve reference foo() with such signature
        |    }
        |  }
        |}""".stripMargin
    }
  )

  def messages(@Language(value = "Scala") code: String) = {
    val file = code.parse
    implicit val mock: AnnotatorHolderMock = new AnnotatorHolderMock(file)

    // TODO use the general annotate() method
    file.depthFirst().filterByType[ScReference].foreach {
      ScReferenceAnnotator.annotateReference(_)
    }
    mock.annotations
  }
}
