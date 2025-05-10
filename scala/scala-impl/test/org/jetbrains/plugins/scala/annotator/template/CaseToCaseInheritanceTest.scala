package org.jetbrains.plugins.scala.annotator.template

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.{AnnotatorTestBase, Message, ScalaAnnotationHolder}
import org.jetbrains.plugins.scala.annotator.element.ScTemplateDefinitionAnnotator
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition

class CaseToCaseInheritanceTest extends AnnotatorTestBase[ScTemplateDefinition] {
  import Message._

  def testCaseToCase(): Unit = {
    val message = ScalaBundle.message("illegal.inheritance.from.case.class", "B", "A")

    val expectation: PartialFunction[List[Message], Unit] = {
      case Error("A", `message`) :: Nil =>
    }

    assertMatches(messages("case class A(); case class B() extends A(); B()"))(expectation)
  }

  def testIndirectCaseToCase(): Unit = {
    val message = ScalaBundle.message("illegal.inheritance.from.case.class", "C", "A")

    val expectation: PartialFunction[List[Message], Unit] = {
      case Error("B", `message`) :: Nil =>
    }

    assertMatches(messages(
      """
        |case class A(a : Int)
        |class B extends A(2)
        |case class C(z: Int) extends B
      """.stripMargin
    ))(expectation)
  }

  override protected def annotate(element: ScTemplateDefinition)
                                 (implicit holder: ScalaAnnotationHolder): Unit =
    ScTemplateDefinitionAnnotator.annotateCaseToCaseInheritance(element)
}