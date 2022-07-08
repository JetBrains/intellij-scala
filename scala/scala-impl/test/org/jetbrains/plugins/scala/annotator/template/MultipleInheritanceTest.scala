package org.jetbrains.plugins.scala
package annotator
package template

import org.jetbrains.plugins.scala.annotator.element.ScTemplateDefinitionAnnotator
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition

class MultipleInheritanceTest extends AnnotatorTestBase[ScTemplateDefinition] {

  def testMultipleTraitInheritance(): Unit = {
    assertNothing(messages("trait T; new T {}"))

    assertNothing(messages("trait A; trait B; new A with B {}"))

    val message = ScalaBundle.message("illegal.inheritance.multiple", "Trait", "T")
    assertMatches(messages("trait T; new T with T")) {
      case Error("T", `message`) :: Error("T", `message`) :: Nil =>
    }

    assertMatches(messages("trait T; new T with T {}")) {
      case Error("T", `message`) :: Error("T", `message`) :: Nil =>
    }

    assertMatches(messages("trait T; class C extends T with T")) {
      case Error("T", `message`) :: Error("T", `message`) :: Nil =>
    }

    assertMatches(messages("trait T; class C extends T with T {}")) {
      case Error("T", `message`) :: Error("T", `message`) :: Nil =>
    }

    assertMatches(messages("trait T; new T with T with T {}")) {
      case Error("T", `message`) :: Error("T", `message`) :: Error("T", `message`) :: Nil =>
    }
  }

  override protected def annotate(element: ScTemplateDefinition)
                                 (implicit holder: ScalaAnnotationHolder): Unit =
    ScTemplateDefinitionAnnotator.annotateMultipleInheritance(element)
}