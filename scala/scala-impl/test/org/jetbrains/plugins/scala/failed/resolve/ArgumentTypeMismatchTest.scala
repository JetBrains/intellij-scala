package org.jetbrains.plugins.scala
package failed.resolve

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.annotator.AnnotatorHolderMock
import org.jetbrains.plugins.scala.annotator.element.ElementAnnotator
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.extensions.{IteratorExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
class ArgumentTypeMismatchTest extends SimpleTestCase {
  override protected def shouldPass: Boolean = false

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

    file.depthFirst().filterByType[ScalaPsiElement].foreach {
      ElementAnnotator.annotate(_, typeAware = true)
    }
    mock.annotations
  }
}
