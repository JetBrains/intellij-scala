package org.jetbrains.plugins.scala
package annotator
package template

import org.jetbrains.plugins.scala.annotator.element.ScTemplateDefinitionAnnotator
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition

class UndefinedMemberTest extends AnnotatorTestBase[ScTemplateDefinition] {
  import Message._

  def testValidHolders(): Unit = {
    assertNothing(messages("class C { def f }"))
    assertNothing(messages("trait T { def f }"))
  }

  def testHolderNew(): Unit = {
    assertMatches(messages("new { def f }")) {
      case Error("def f", IllegalUndefinedMember) :: Nil =>
    }

    assertMatches(messages("new Object { def f }")) {
      case Error("def f", IllegalUndefinedMember) :: Nil =>
    }
  }

  def testHolderObject(): Unit = {
    assertMatches(messages("object O { def f }")) {
      case Error("def f", IllegalUndefinedMember) :: Nil =>
    }

    assertMatches(messages("object O extends Object { def f }")) {
      case Error("def f", IllegalUndefinedMember) :: Nil =>
    }
  }

  def testDeclarations(): Unit = {
    assertMatches(messages("new { def f }")) {
      case Error("def f", IllegalUndefinedMember) :: Nil =>
    }
    assertMatches(messages("new { var v: Object }")) {
      case Error("var v: Object", IllegalUndefinedMember) :: Nil =>
    }
    assertMatches(messages("new { type T }")) {
      case Nil =>
    }
    assertMatches(messages("object O { type T }")) {
      case Nil =>
    }
  }

  def testDefinitions(): Unit = {
    assertNothing(messages("new { def f = null }"))
    assertNothing(messages("new { var v: Object = null }"))
    assertNothing(messages("new { type T = Any }"))
  }

  override protected def annotate(element: ScTemplateDefinition)
                                 (implicit holder: ScalaAnnotationHolder): Unit =
    ScTemplateDefinitionAnnotator.annotateUndefinedMember(element)

  private val IllegalUndefinedMember = ScalaBundle.message("illegal.undefined.member")
}