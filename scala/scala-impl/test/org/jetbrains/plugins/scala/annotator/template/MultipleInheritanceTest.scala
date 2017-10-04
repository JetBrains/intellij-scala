package org.jetbrains.plugins.scala.annotator.template

import org.jetbrains.plugins.scala.annotator.{AnnotatorTestBase, Error}

/**
 * Pavel Fatin
 */

class MultipleInheritanceTest extends AnnotatorTestBase(MultipleInheritance) {
  private val Message = "Trait (\\w+) inherited multiple times".r

  def testMultipleTraitInheritance() {
    assertNothing(messages("trait T; new T {}"))

    assertNothing(messages("trait A; trait B; new A with B {}"))

    assertMatches(messages("trait T; new T with T")) {
      case Error("T", Message("T")) ::
              Error("T", Message("T")) :: Nil =>
    }

    assertMatches(messages("trait T; new T with T {}")) {
      case Error("T", Message("T")) ::
              Error("T", Message("T")) :: Nil =>
    }

    assertMatches(messages("trait T; class C extends T with T")) {
      case Error("T", Message("T")) ::
              Error("T", Message("T")) :: Nil =>
    }

    assertMatches(messages("trait T; class C extends T with T {}")) {
      case Error("T", Message("T")) ::
              Error("T", Message("T")) :: Nil =>
    }

    assertMatches(messages("trait T; new T with T with T {}")) {
      case Error("T", Message("T")) ::
              Error("T", Message("T")) ::
              Error("T", Message("T")) :: Nil =>
    }
  }
}