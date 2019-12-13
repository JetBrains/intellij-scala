package org.jetbrains.plugins.scala
package annotator
package template

import org.jetbrains.plugins.scala.annotator.element.ScTemplateDefinitionAnnotator
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition

/**
  * Pavel Fatin
  */
class NeedsToBeAbstractTest extends AnnotatorTestBase[ScTemplateDefinition] {

  def testFine(): Unit = {
    assertNothing(messages("class C"))
    assertNothing(messages("class C {}"))
    assertNothing(messages("trait T"))
    assertNothing(messages("trait T {}"))
    assertNothing(messages("abstract class C"))
    assertNothing(messages("abstract class C {}"))
    assertNothing(messages("abstract class C { def f }"))
    assertNothing(messages("trait T { def f }"))
  }

  def testSkipNew(): Unit = {
    assertNothing(messages("trait T { def f }; new Object with T"))
  }

  def testSkipObject(): Unit = {
    assertNothing(messages("trait T { def f }; object O extends T"))
  }

  def testUndefinedMember(): Unit = {
    val message = this.message("Class", "C", "f: Unit", "Holder.C")

    assertMatches(messages("class C { def f }")) {
      case Error("C", `message`) :: Nil =>
    }
  }

  def testUndefinedInheritedMember(): Unit = {
    val message = this.message("Class", "C", "f: Unit", "Holder.T")

    assertMatches(messages("trait T { def f }; class C extends T")) {
      case Error("C", `message`) :: Nil =>
    }

    assertMatches(messages("trait T { def f }; class C extends T {}")) {
      case Error("C", `message`) :: Nil =>
    }
  }

  def testNeedsToBeAbstractPlaceDiffer(): Unit = {
    val message = this.message("Class", "C", "b: Unit", "Holder.B")
    val reversedMessage = this.message("Class", "C", "a: Unit", "Holder.A")

    assertMatches(messages("trait A { def a }; trait B { def b }; class C extends A with B {}")) {
      case Error("C", `message`) :: Nil =>
      case Error("C", `reversedMessage`) :: Nil =>
    }
  }

  def testObjectOverrideDef(): Unit = {
    assertMatches(messages("trait A { def a }; class D extends A { object a };")) {
      case Nil =>
    }
  }

  override protected def annotate(element: ScTemplateDefinition)
                                 (implicit holder: ScalaAnnotationHolder): Unit =
    ScTemplateDefinitionAnnotator.annotateNeedsToBeAbstract(element)

  private def message(params: String*) =
    ScalaBundle.message("member.implementation.required", params: _*)
}