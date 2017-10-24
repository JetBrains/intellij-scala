package org.jetbrains.plugins.scala.annotator.template

import org.jetbrains.plugins.scala.annotator.{AnnotatorTestBase, Error}

/**
 * Pavel Fatin
 */

class NeedsToBeTraitTest extends AnnotatorTestBase(NeedsToBeTrait) {
  private val Message = "Class (\\w+) needs to be trait to be mixed in".r

  def testNeedsToBeTrait() {
    assertNothing(messages("class C; trait T; new C with T"))
    assertNothing(messages("class C; trait T1; trait T2; new C with T1 with T2"))

    assertMatches(messages("class C; class T; new C with T")) {
      case Error("T", Message("T")) :: Nil =>
    }
    assertMatches(messages("class C; class T1; class T2; new C with T1 with T2")) {
      case Error("T1", Message("T1")) ::
              Error("T2", Message("T2")) :: Nil =>
    }
  }

  def testNeedsToBeTraitAndMultipleTraitInheritance() {
    assertMatches(messages("class C; new C with C")) {
      case Error("C", Message("C")) :: Nil =>
    }
  }
}