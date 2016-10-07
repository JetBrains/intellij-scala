package org.jetbrains.plugins.scala.failed.resolve

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.annotator.{AnnotatorHolderMock, ApplicationAnnotator}
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.junit.experimental.categories.Category

/**
  * @author Roman.Shein
  * @since 25.03.2016.
  */
@Category(Array(classOf[PerfCycleTests]))
class ArgumentTypeMismatchTest extends SimpleTestCase {
  def testSCL4687() = {
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
    assert(messages(code).isEmpty)
  }
  
  def testSCL9686() = assert(
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
    }.isEmpty
  )

  def messages(@Language(value = "Scala") code: String) = {
    val annotator = new ApplicationAnnotator {}
    val file = code.parse
    val mock = new AnnotatorHolderMock(file)

    file.depthFirst.filter(elem => elem.isInstanceOf[ScReferenceExpression]).foreach {
      case ref: ScReferenceExpression => annotator.annotateReference(ref, mock)
    }
    mock.annotations
  }
}
