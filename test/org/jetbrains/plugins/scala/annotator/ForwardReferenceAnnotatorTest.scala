package org.jetbrains.plugins.scala.annotator

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement

/**
  * @author Alefas
  * @since 02/11/16
  */
class ForwardReferenceAnnotatorTest extends SimpleTestCase {
  final val Header = ""

  def testFine(): Unit = {
    assertNothing(messages(
      """
        |{
        |  object A { B }
        |  object B { A }
        |}
      """.stripMargin))

    assertNothing(messages(
      """
        |{
        |  object A { B }
        |  A
        |  object B { A }
      """.stripMargin))
  }

  def testWrong(): Unit = {
    assertMatches(messages(
      """
        |{
        |  object A { B }
        |  val x = A
        |  object B { A }
        |}
      """.stripMargin)) {
      case Error("B", ForwardReference()) :: Nil =>
    }
  }

  def messages(@Language(value = "Scala", prefix = Header) code: String): List[Message] = {
    val annotator = new ScalaAnnotator() {}
    val parse: ScalaFile = (Header + code).parse

    val mock = new AnnotatorHolderMock(parse)

    parse.depthFirst.filterByType(classOf[ScReferenceElement]).foreach { ref =>
      annotator.annotate(ref, mock)
    }

    mock.errorAnnotations
  }

  val ForwardReference = ContainsPattern("Wrong forward reference")
}
