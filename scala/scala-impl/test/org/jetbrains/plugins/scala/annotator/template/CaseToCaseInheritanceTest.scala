package org.jetbrains.plugins.scala.annotator.template

import org.jetbrains.plugins.scala.annotator.element.ScTemplateDefinitionAnnotator
import org.jetbrains.plugins.scala.annotator.{AnnotatorTestBase, ScalaAnnotationHolder}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition

class CaseToCaseInheritanceTest extends AnnotatorTestBase[ScTemplateDefinition] {

  def testCaseToCase(): Unit = {
    assertMessagesText(
      """case class A()
        |case class B() extends A()
        |B()""".stripMargin,
      """Error(A,Case class 'B' has case ancestor 'A', but case-to-case inheritance is prohibited)"""
    )
  }

  def testIndirectCaseToCase(): Unit = {
    assertMessagesText(
      """case class A(a : Int)
        |class B extends A(2)
        |case class C(z: Int) extends B""".stripMargin,
      """Error(B,Case class 'C' has case ancestor 'A', but case-to-case inheritance is prohibited)"""
    )
  }

  override protected def annotate(element: ScTemplateDefinition)
                                 (implicit holder: ScalaAnnotationHolder): Unit =
    ScTemplateDefinitionAnnotator.annotateCaseToCaseInheritance(element)
}