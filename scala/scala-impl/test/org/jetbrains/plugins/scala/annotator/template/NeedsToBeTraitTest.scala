package org.jetbrains.plugins.scala
package annotator
package template

import org.jetbrains.plugins.scala.annotator.element.ScTemplateDefinitionAnnotator
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition

class NeedsToBeTraitTest extends AnnotatorTestBase[ScTemplateDefinition] {

  def testNeedsToBeTrait(): Unit = {
    assertNothing(messages("class C; trait T; new C with T"))
    assertNothing(messages("class C; trait T1; trait T2; new C with T1 with T2"))

    val firstMessage = message("Class", "T")
    assertMatches(messages("class C; class T; new C with T")) {
      case Error("T", `firstMessage`) :: Nil =>
    }

    val secondMessage = message("Class", "T1")
    val thirdMessage = message("Class", "T2")
    assertMatches(messages("class C; class T1; class T2; new C with T1 with T2")) {
      case Error("T1", `secondMessage`) ::
        Error("T2", `thirdMessage`) :: Nil =>
    }
  }

  def testNeedsToBeTraitAndMultipleTraitInheritance(): Unit = {
    val message = this.message("Class", "C")
    assertMatches(messages("class C; new C with C")) {
      case Error("C", `message`) :: Nil =>
    }
  }

  override protected def annotate(element: ScTemplateDefinition)
                                 (implicit holder: ScalaAnnotationHolder): Unit =
    ScTemplateDefinitionAnnotator.annotateNeedsToBeTrait(element)

  private def message(params: String*) =
    ScalaBundle.message("illegal.mixin", params: _*)
}