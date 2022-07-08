package org.jetbrains.plugins.scala.annotator

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference

class ForwardReferenceAnnotatorTest extends AnnotatorSimpleTestCase {
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
    val annotator = ScalaAnnotator.forProject
    val parse: ScalaFile = (Header + code).parse

    implicit val mock: AnnotatorHolderMock = new AnnotatorHolderMock(parse)

    parse
      .depthFirst()
      .filterByType[ScReference]
      .foreach(annotator.annotate)

    mock.errorAnnotations
  }

  val ForwardReference = ContainsPattern("Wrong forward reference")
}
