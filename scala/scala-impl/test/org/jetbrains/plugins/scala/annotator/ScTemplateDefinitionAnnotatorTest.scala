package org.jetbrains.plugins.scala
package annotator

import org.jetbrains.plugins.scala.annotator.element.ScTemplateDefinitionAnnotator
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
class ScTemplateDefinitionAnnotatorTest extends SimpleTestCase {
  import Message._

  def testTraitPassingConstructorParameters(): Unit =
    assertMessages(messages(
      """
        |trait Foo(x: Int)
        |trait Bar extends Foo(123)
        |
        |trait Baz
        |trait Qux extends Baz with Foo(123)
        |""".stripMargin)
    )(
      Error("Foo(123)", "Trait Bar may not call constructor of Foo"),
      Error("Foo(123)", "Trait Qux may not call constructor of Foo")
    )

  def testDontCallConstructorTwice(): Unit = {
    assertNothing(
      messages(
        """
          |trait Foo(x: Int)
          |class F extends Foo(123)
          |case class Bar extends F with Foo
          |""".stripMargin
      )
    )

    assertMessages(
      messages(
        """
          |trait Foo(x: Int)
          |class F extends Foo(1)
          |class Baz extends F with Foo(2)
          |
          |""".stripMargin
      )
    )(
      Error("Foo(2)", "Trait Foo is already implemented by superclass F,its constructor cannot be called again")
    )
  }

  def testIndirectImplementation(): Unit = {
    assertMessages(messages(
      """
        |trait Greeting(val name: String)
        |trait FormalGreeting extends Greeting
        |class E extends FormalGreeting
        |""".stripMargin
    ))(
      Error("E", "Parameterized trait Greeting is indirectly implemented,needs to be implemented directly so that arguments can be passed")
    )
  }

  def testSCL21122(): Unit =
    assertNothing(
      messages(
        """
          |object TraitWithDefaultPramas {
          |  trait IFoo(x: String = "foo") {
          |    val name = x
          |  }
          |
          |  class Foo extends IFoo // compile passed, but idea reports error
          |}
          |""".stripMargin
      )
    )

  def messages(code: String): List[Message] = {
    val file: ScalaFile = code.parse

    implicit val mock: AnnotatorHolderMock = new AnnotatorHolderMock(file)

    file.depthFirst().filterByType[ScTemplateDefinition].foreach { pte =>
      ScTemplateDefinitionAnnotator.annotate(pte, typeAware = true)
    }

    mock.annotations
  }
}
