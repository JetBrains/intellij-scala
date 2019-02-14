package org.jetbrains.plugins.scala.annotator.template

import org.jetbrains.plugins.scala.annotator.{AnnotatorTestBase, Error}
import org.jetbrains.plugins.scala.lang.psi.annotator.ScTemplateDefinitionAnnotator._
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.ScTemplateDefinitionImpl

/**
 * Pavel Fatin
 */

class ObjectCreationImpossibleTest extends AnnotatorTestBase[ScTemplateDefinitionImpl](_.annotateObjectCreationImpossible(_)) {
  def testFineNew() {
    assertNothing(messages("class C; new C"))
    assertNothing(messages("class C; new C {}"))
    assertNothing(messages("class C; trait T; new C with T"))
    assertNothing(messages("class C; trait T; new C with T {}"))
  }

  def testFineObject() {
    assertNothing(messages("class C; object O extends C"))
    assertNothing(messages("class C; object O extends C {}"))
    assertNothing(messages("class C; trait T; object O extends C with T"))
    assertNothing(messages("class C; trait T; object O extends C with T {}"))
  }

  def testTypeSkipDeclarations() {
    assertNothing(messages("class C { def f }"))
  }

  def testSkipAbstractInstantiations() {
    assertNothing(messages("trait T; new T"))
  }

  def testSkipConcrete() {
    assertNothing(messages("class C { def f }; new C"))
    assertNothing(messages("class C { def f }; new C {}"))
    assertNothing(messages("class C { def f }; new Object with C"))
    assertNothing(messages("class C { def f }; new Object with C {}"))
  }

  def testSkipInvalidDirect() {
    assertNothing(messages("new { def f }"))
    assertNothing(messages("new Object { def f }"))
    assertNothing(messages("object O { def f }"))
  }

  def testUndefinedMember() {
    val Message = objectCreationImpossibleMessage(("f: Unit", "Holder.T"))

    assertMatches(messages("trait T { def f }; new T {}")) {
      case Error("T", Message) :: Nil =>
    }
  }

  def testUndefinedMemberObject() {
    val Message = objectCreationImpossibleMessage(("f: Unit", "Holder.T"))

    assertMatches(messages("trait T { def f }; object O extends T {}")) {
      case Error("O", Message) :: Nil =>
    }
  }

  def testUndefinedAndWith(){
    val Message = objectCreationImpossibleMessage(("f: Unit", "Holder.T"))

    assertMatches(messages("trait T { def f }; new Object with T {}")) {
      case Error("Object", Message) :: Nil =>
    }
  }

  def testNeedsToBeAbstractPlaceDiffer() {
    val Message = objectCreationImpossibleMessage(
      ("b: Unit", "Holder.B"), ("a: Unit", "Holder.A"))
    val ReversedMessage = objectCreationImpossibleMessage(
      ("a: Unit", "Holder.A"), ("b: Unit", "Holder.B"))

    assertMatches(messages("trait A { def a }; trait B { def b }; new A with B {}")) {
      case Error("A", Message) :: Nil =>
      case Error("A", ReversedMessage) :: Nil =>
    }
  }

  def testSkipTypeDeclarationSCL2887() {
    assertNothing(messages("trait A { type a }; new A {}"))
  }
}