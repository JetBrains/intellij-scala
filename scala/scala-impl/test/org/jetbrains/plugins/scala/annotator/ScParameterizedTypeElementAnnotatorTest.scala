package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.annotator.element.ScParameterizedTypeElementAnnotator
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScParameterizedTypeElement

class ScParameterizedTypeElementAnnotatorTest extends SimpleTestCase {
  def testTooFewTypeParameter(): Unit = {
    assertMessagesInAllContexts("Test[Int]")(
      Error("t]", "Unspecified type parameters: Y")
    )
    assertMessagesInAllContexts("Test[]")(
      Error("[]", "Unspecified type parameters: X, Y")
    )
  }

  def testTooManyTypeParameter(): Unit = {
    assertMessagesInAllContexts("Test[Int, Int, Int]")(
      Error(", I", "Too many type arguments for Test[X, Y]")
    )
    assertMessagesInAllContexts("Test[Int, Int, Boolean, Int]")(
      Error(", B", "Too many type arguments for Test[X, Y]")
    )

    assertMessagesInAllContexts("A[Int]")(
      Error("[Int]", "A does not take type arguments")
    )
  }

  def testBoundViolation(): Unit = {
    assertMessagesInAllContexts("Test2[A, A]")(
      Error("A", "Type A does not conform to upper bound B of type parameter X"),
    )

    assertMessagesInAllContexts("Test2[C, C]")(
      Error("C", "Type C does not conform to lower bound B of type parameter Y"),
    )

    assertMessagesInAllContexts("Test2[A, C]")(
      Error("A", "Type A does not conform to upper bound B of type parameter X"),
      Error("C", "Type C does not conform to lower bound B of type parameter Y"),
    )
  }

  def assertMessagesInAllContexts(typeText: String)(expected: Message*): Unit = {
    val Header =
      """
        |trait A
        |trait B extends A
        |trait C extends B
        |
        |class Test[X, Y]
        |class Test2[X <: B, Y >: B]
        |""".stripMargin

    val contexts = Seq(
      s"type W = $typeText",
      s"def w(arg: $typeText): Unit = ()",
      s"def w[Q](arg: $typeText): Unit = ()",
      s"def w(): $typeText = w()",
      s"new $typeText",
      s"class Blub extends $typeText",
      s"class W[Q <: $typeText]",
      s"class W[Test[X, Y], Test2[X <: B, Y >: B], Q <: $typeText]",
    )

    for (context <- contexts) {
      assertMessages(messages(Header + context))(expected: _*)
    }
  }

  def messages(code: String): List[Message] = {
    val file: ScalaFile = code.parse

    implicit val mock: AnnotatorHolderMock = new AnnotatorHolderMock(file)

    file.depthFirst().filterByType[ScParameterizedTypeElement].foreach { pte =>
      ScParameterizedTypeElementAnnotator.annotate(pte, typeAware = true)
    }

    mock.annotations
  }
}
