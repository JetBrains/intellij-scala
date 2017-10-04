package org.jetbrains.plugins.scala.annotator.template

import org.jetbrains.plugins.scala.annotator.{AnnotatorTestBase, Error}

/**
 * Pavel Fatin
 */

class UndefinedMemberTest extends AnnotatorTestBase(UndefinedMember) {
  def testValidHolders() {
    assertNothing(messages("class C { def f }"))
    assertNothing(messages("trait T { def f }"))
  }

  def testHolderNew() {
    assertMatches(messages("new { def f }")) {
      case Error("def f", UndefinedMember.Message) :: Nil =>
    }

    assertMatches(messages("new Object { def f }")) {
      case Error("def f", UndefinedMember.Message) :: Nil =>
    }
  }

  def testHolderObject() {
    assertMatches(messages("object O { def f }")) {
      case Error("def f", UndefinedMember.Message) :: Nil =>
    }

    assertMatches(messages("object O extends Object { def f }")) {
      case Error("def f", UndefinedMember.Message) :: Nil =>
    }
  }

  def testDeclarations() {
    assertMatches(messages("new { def f }")) {
      case Error("def f", UndefinedMember.Message) :: Nil =>
    }
    assertMatches(messages("new { var v: Object }")) {
      case Error("var v: Object", UndefinedMember.Message) :: Nil =>
    }
    assertMatches(messages("new { type T }")) {
      case Error("type T", UndefinedMember.Message) :: Nil =>
    }
  }

  def testDefinitions() {
    assertNothing(messages("new { def f = null }"))
    assertNothing(messages("new { var v: Object = null }"))
    assertNothing(messages("new { type T = Any }"))
  }
}