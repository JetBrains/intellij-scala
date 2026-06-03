package org.jetbrains.plugins.scala
package codeInspection

import org.jetbrains.plugins.scala.base.SimpleTestCase

class SuspiciousNewLineInMethodCallTest extends SimpleTestCase {

  def testStub(): Unit = {}

  /*val message = ScalaBundle.message("suspicicious.newline")

  def testNewlineAfterInfixOperation = assertMatches(messages("test foo\n *   1")) {
    case Problem(_, message) :: Nil =>
  }

  def testNewlineAfterInfixOperationOkInParens = assertMatches(messages("(test foo\n *   1)")) {
    case  Nil =>
  }

  def testNewlineBeforeOpenBrace = assertMatches(messages("1\n{\n  foo\n}")) {
    case Problem(_, message) :: Nil =>
  }


  def testNewlineBeforeOpenBraceOkayInParens = assertMatches(messages("(1\n{\n  foo\n})")) {
    case Nil =>
  }

  def testNewlineOkayAfterOpenParen = assertMatches(messages("Map(\n1 -> 2)")) {
    case Nil =>
  }

  def testBetweenCurriedParamList = assertMatches(messages("Map(\n1 -> 2)")) {
    case Nil =>
  }

  def messages(code: String): List[Problem] = {
    val inspection = new SuspiciousNewLineInMethodCall()

    val file = ("object test {" + code + "}").parse

    val manager = InspectionManager.getInstance(file.getProject)
    val problems: Seq[Problem] = inspection.checkFileInternal(file, manager, false)
    problems.toList
  }*/
}