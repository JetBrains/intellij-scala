package org.jetbrains.plugins.scala.annotator.template

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.{AnnotatorTestBase, Error, Message}

/**
 * Pavel Fatin
 */

class FinalClassInheritanceTest extends AnnotatorTestBase(FinalClassInheritance) {
  private val Message = "Illegal inheritance from final class (\\w+)".r
  private val ValueClassMessage = ScalaBundle.message("illegal.inheritance.from.value.class", "C")

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
    val expectation: PartialFunction[List[Message], Unit] = {
      case Error("C", Message("C")) :: Nil =>
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
    val expectation: PartialFunction[List[Message], Unit] = {
      case Error("C", ValueClassMessage) :: Nil =>
    }

    assertNothing(messages("class C(val x: Int) extends AnyVal; new C"))
    assertMatches(messages("class C(val x: Int) extends AnyVal; new C {}"))(expectation)
    assertMatches(messages("class C(val x: Int) extends AnyVal; class X extends C(2)"))(expectation)
  }
}