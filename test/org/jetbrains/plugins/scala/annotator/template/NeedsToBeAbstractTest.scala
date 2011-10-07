package org.jetbrains.plugins.scala.annotator.template

import org.jetbrains.plugins.scala.annotator.AnnotatorTestBase
import org.jetbrains.plugins.scala.annotator.Error

/**
 * Pavel Fatin
 */
class NeedsToBeAbstractTest extends AnnotatorTestBase(NeedsToBeAbstract) {
  def testFine() {
    assertNothing(messages("class C"))
    assertNothing(messages("class C {}"))
    assertNothing(messages("trait T"))
    assertNothing(messages("trait T {}"))
    assertNothing(messages("abstract class C"))
    assertNothing(messages("abstract class C {}"))
    assertNothing(messages("abstract class C { def f }"))
    assertNothing(messages("trait T { def f }"))
  }

  def testSkipNew() {
    assertNothing(messages("trait T { def f }; new Object with T"))
  }

  def testSkipObject() {
    assertNothing(messages("trait T { def f }; object O extends T"))
  }

  def testUndefinedMember() {
    val Message = NeedsToBeAbstract.message (
      "Class", "C", ("f: Unit", "Holder.C"))

    assertMatches(messages("class C { def f }")) {
      case Error("C", Message) :: Nil =>
    }
  }

  def testUndefinedInheritedMember() {
    val Message = NeedsToBeAbstract.message (
      "Class", "C", ("f: Unit", "Holder.T"))

    assertMatches(messages("trait T { def f }; class C extends T")) {
      case Error("C", Message) :: Nil =>
    }

    assertMatches(messages("trait T { def f }; class C extends T {}")) {
      case Error("C", Message) :: Nil =>
    }
  }

  def testNeedsToBeAbstractPlaceDiffer() {
    val Message = NeedsToBeAbstract.message (
      "Class", "C", ("b: Unit", "Holder.B"), ("a: Unit", "Holder.A"))
    val ReversedMessage = NeedsToBeAbstract.message (
      "Class", "C", ("a: Unit", "Holder.A"), ("b: Unit", "Holder.B"))

    assertMatches(messages("trait A { def a }; trait B { def b }; class C extends A with B {}")) {
      case Error("C", Message) :: Nil =>
      case Error("C", ReversedMessage) :: Nil =>
    }
  }

  def testObjectOverrideDef() {
    assertMatches(messages("trait A { def a }; class D extends A { object a };")) {
      case Nil =>
    }
  }
}