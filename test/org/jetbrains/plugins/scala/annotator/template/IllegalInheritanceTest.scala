package org.jetbrains.plugins.scala.annotator.template

import org.jetbrains.plugins.scala.annotator.AnnotatorTestBase
import org.jetbrains.plugins.scala.annotator.Error

/**
 * Pavel Fatin
 */

class IllegalInheritanceTest extends AnnotatorTestBase(IllegalInheritance) {
  def testFine() {
    assertNothing(messages("class C"))
    assertNothing(messages("trait X; class C { self: X => }"))
    assertNothing(messages("trait T; class C extends T"))
    assertNothing(messages("trait X; trait T { self: X => }; class C extends X with T"))
    assertNothing(messages("trait X; trait T { self: X => }; class C extends T with X"))
    assertNothing(messages("trait X; trait T { self: X => }; class C extends T { self: X => }"))
    assertNothing(messages("trait X; trait Y extends X; trait T { self: X => }; class C extends T { self: Y => }"))
    assertNothing(messages("trait X[A]; trait Y[A] { self: X[A] => }; class Z extends X[String]; " +
      "object A {new Z with Y[String]}"))
  }

  def testIllegalInheritance() {
    val m1 = IllegalInheritance.Message("Holder.C", "Holder.X")
    assertMatches(messages("trait X; trait T { self: X => }; class C extends T")) {
      case Error("T", _) :: Nil =>
    }

    val m2 = IllegalInheritance.Message("Holder.C", "Holder.X")
    assertMatches(messages("trait X; trait T { self: X => }; class C extends Object with T")) {
      case Error("T", _) :: Nil =>
    }

    val m3 = IllegalInheritance.Message("Holder.Y", "Holder.X")
    assertMatches(messages("trait X; trait Y; trait T { self: X => }; class C extends T { self: Y => }")) {
      case Error("T", _) :: Nil =>
    }

    val m4 = IllegalInheritance.Message("Holder.X", "Holder.Y")
    assertMatches(messages("trait X; trait Y extends X; trait T { self: Y => }; class C extends T { self: X => }")) {
      case Error("T", _) :: Nil =>
    }
  }
}