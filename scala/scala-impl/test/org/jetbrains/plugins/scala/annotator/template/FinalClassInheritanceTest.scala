package org.jetbrains.plugins.scala
package annotator
package template

import org.jetbrains.plugins.scala.annotator.element.ScTemplateDefinitionAnnotator
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition

class FinalClassInheritanceTest extends AnnotatorTestBase[ScTemplateDefinition] {

  def testOrdinaryClass(): Unit = {
    assertNothing(messages("class C; new C"))
    assertNothing(messages("class C; new C {}"))
    assertNothing(messages("class C; new C with Object"))
    assertNothing(messages("class C; new C with Object {}"))
    assertNothing(messages("class C; new Object with C"))
    assertNothing(messages("class C; new Object with C {}"))
    assertNothing(messages("class C; class X extends C"))
    assertNothing(messages("class C; class X extends C {}"))
    assertNothing(messages("class C; class X extends C with Object"))
    assertNothing(messages("class C; class X extends C with Object {}"))
    assertNothing(messages("class C; class X extends Object with C"))
    assertNothing(messages("class C; class X extends Object with C {}"))
  }

  def testFinalClass(): Unit = {
    val message = ScalaBundle.message("illegal.inheritance.from.final.kind", "class", "C")
    val expectation: PartialFunction[List[Message], Unit] = {
      case Error("C", `message`) :: Nil =>
    }

    assertNothing(messages("final class C; new C"))
    assertMatches(messages("final class C; new C {}"))(expectation)
    assertNothing(messages("final class C; new C with Object"))
    assertMatches(messages("final class C; new C with Object {}"))(expectation)
    assertNothing(messages("final class C; new Object with C"))
    assertMatches(messages("final class C; new Object with C {}"))(expectation)
    assertMatches(messages("final class C; class X extends C"))(expectation)
    assertMatches(messages("final class C; class X extends C {}"))(expectation)
    assertMatches(messages("final class C; class X extends C with Object"))(expectation)
    assertMatches(messages("final class C; class X extends C with Object {}"))(expectation)
    assertMatches(messages("final class C; class X extends Object with C"))(expectation)
    assertMatches(messages("final class C; class X extends Object with C {}"))(expectation)
  }

  def testValueClass(): Unit = {
    val message = ScalaBundle.message("illegal.inheritance.from.value.class", "C")

    val expectation: PartialFunction[List[Message], Unit] = {
      case Error("C", `message`) :: Nil =>
    }

    assertNothing(messages("class C(val x: Int) extends AnyVal; new C"))
    assertMatches(messages("class C(val x: Int) extends AnyVal; new C {}"))(expectation)
    assertMatches(messages("class C(val x: Int) extends AnyVal; class X extends C(2)"))(expectation)
  }

  override protected def annotate(element: ScTemplateDefinition)
                                 (implicit holder: ScalaAnnotationHolder): Unit =
    ScTemplateDefinitionAnnotator.annotateFinalClassInheritance(element)
}