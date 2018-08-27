package org.jetbrains.plugins.scala
package annotator
package template

/**
 * Pavel Fatin
 */
class UndefinedMemberTest extends AnnotatorTestBase(UndefinedMember) {

  def testValidHolders(): Unit = {
    assertNothing(messages("class C { def f }"))
    assertNothing(messages("trait T { def f }"))
  }

  def testHolderNew(): Unit = {
    assertMatches(messages("new { def f }")) {
      case Error("def f", Message) :: Nil =>
    }

    assertMatches(messages("new Object { def f }")) {
      case Error("def f", Message) :: Nil =>
    }
  }

  def testHolderObject(): Unit = {
    assertMatches(messages("object O { def f }")) {
      case Error("def f", Message) :: Nil =>
    }

    assertMatches(messages("object O extends Object { def f }")) {
      case Error("def f", Message) :: Nil =>
    }
  }

  def testDeclarations(): Unit = {
    assertMatches(messages("new { def f }")) {
      case Error("def f", Message) :: Nil =>
    }
    assertMatches(messages("new { var v: Object }")) {
      case Error("var v: Object", Message) :: Nil =>
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

  private val Message = ScalaBundle.message("illegal.undefined.member")
}