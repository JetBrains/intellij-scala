package org.jetbrains.plugins.scala
package annotator
package template

import org.jetbrains.plugins.scala.annotator.element.ScNewTemplateDefinitionAnnotator
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition

class AbstractInstantiationTest extends AnnotatorTestBase[ScNewTemplateDefinition] {

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

  def testAbstractClass(): Unit = {
    val firstMessage = message("Trait", "T")
    assertMatches(messages("trait T; new T")) {
      case Error("T", `firstMessage`) :: Nil =>
    }
    val secondMessage = message("Class", "C")
    assertMatches(messages("abstract class C; new C")) {
      case Error("C", `secondMessage`) :: Nil =>
    }
    assertNothing(messages("abstract class C; new C {}"))
    assertNothing(messages("abstract class C; new C with Object"))
    assertNothing(messages("abstract class C; new C with Object {}"))
    assertNothing(messages("abstract class C; new Object with C"))
    assertNothing(messages("abstract class C; new Object with C {}"))
    assertNothing(messages("abstract class C; class X extends C"))
    assertNothing(messages("abstract class C; class X extends C {}"))
    assertNothing(messages("abstract class C; class X extends C with Object"))
    assertNothing(messages("abstract class C; class X extends C with Object {}"))
    assertNothing(messages("abstract class C; class X extends Object with C"))
    assertNothing(messages("abstract class C; class X extends Object with C {}"))
  }

  def testAbstractClassEarlyDefinition(): Unit = {
    val firstMessage = message("Class", "C")
    assertMatches(messages("abstract class C; new {} with C")) {
      case Error("C", `firstMessage`) :: Nil =>
    }
    assertNothing(messages("abstract class C; new { val a = 0 } with C"))
  }

  override protected def annotate(element: ScNewTemplateDefinition)
                                 (implicit holder: ScalaAnnotationHolder): Unit =
    ScNewTemplateDefinitionAnnotator.annotateAbstractInstantiation(element)

  private def message(params: String*) =
    ScalaBundle.message("illegal.instantiation", params: _*)
}