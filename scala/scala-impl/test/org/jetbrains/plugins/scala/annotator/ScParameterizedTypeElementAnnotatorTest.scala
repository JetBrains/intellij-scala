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
  }

  def assertMessagesInAllContexts(typeText: String)(expected: Message*): Unit = {
    val Header =
      """
        |class Test[X, Y]
        |""".stripMargin

    val contexts = Seq(
      s"type X = $typeText",
      s"def x(arg: $typeText): Unit = ()",
      s"def x[Q](arg: $typeText): Unit = ()",
      s"def x(): $typeText = x()",
      s"new $typeText",
      s"class Blub extends $typeText",
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
