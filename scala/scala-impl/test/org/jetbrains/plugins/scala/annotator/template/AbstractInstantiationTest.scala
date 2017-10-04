package org.jetbrains.plugins.scala.annotator.template

import org.jetbrains.plugins.scala.annotator.{AnnotatorTestBase, Error}


/**
 * Pavel Fatin
 */

class AbstractInstantiationTest extends AnnotatorTestBase(AbstractInstantiation.THIS) {
  private val Message = "(\\w+\\s\\w+) is abstract; cannot be instantiated".r

  def testOrdinaryClass() {
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

  def testAbstractClass() {
    assertMatches(messages("trait T; new T")) {
      case Error("T", Message("Trait T")) :: Nil =>
    }
    assertMatches(messages("abstract class C; new C")) {
      case Error("C", Message("Class C")) :: Nil =>
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

  def testAbstractClassEarlyDefinition() {
    assertMatches(messages("abstract class C; new {} with C")) {
      case Error("C", Message("Class C")) :: Nil =>
    }
    assertNothing(messages("abstract class C; new { val a = 0 } with C"))
  }
}