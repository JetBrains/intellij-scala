package org.jetbrains.plugins.scala.lang.completion3

import com.intellij.codeInsight.completion.CompletionType
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightTestBase
import org.junit.Assert

/**
 * User: Alexander Podkhalyuzin
 * Date: 04.01.12
 */

class ScalaKeywordCompletionTest extends ScalaCodeInsightTestBase {
  def testPrivateVal() {
    val fileText =
      """
      |class A {
      |  private va<caret>
      |}
      """

    val resultText =
      """
      |class A {
      |  private val <caret>
      |}
      """

    doCompletionTest(fileText, resultText, "val")
  }

  def testPrivateThis() {
    val fileText =
      """
        |class A {
        |  pr<caret>
        |}
      """

    val resultText =
      """
        |class A {
        |  private[<caret>]
        |}
      """

    doCompletionTest(fileText, resultText, "private", '[')
  }

  def testFirstVal() {
    val fileText =
      """
      |class A {
      |  def foo() {
      |    va<caret>vv.v
      |  }
      |}
      """

    val resultText =
      """
      |class A {
      |  def foo() {
      |    val <caret>vv.v
      |  }
      |}
      """

    doCompletionTest(fileText, resultText, "val", ' ')
  }

  def testIfAfterCase() {
    val fileText =
      """
      |1 match {
      |  case a if<caret>
      |}
      """

    val resultText =
      """
      |1 match {
      |  case a if <caret>
      |}
      """

    doCompletionTest(fileText, resultText, "if", ' ')
  }

  def testValUnderCaseClause() {
    val fileText =
      """
      |1 match {
      |  case 1 =>
      |    val<caret>
      |}
      """

    val resultText =
      """
      |1 match {
      |  case 1 =>
      |    val <caret>
      |}
      """

    doCompletionTest(fileText, resultText, "val", ' ')
  }

  def testDefUnderCaseClause() {
    val fileText =
      """
      |1 match {
      |  case 1 =>
      |    def<caret>
      |}
      """

    val resultText =
      """
      |1 match {
      |  case 1 =>
      |    def <caret>
      |}
      """

    doCompletionTest(fileText, resultText, "def", ' ')
  }

  def testIfParentheses() {
    val fileText =
      """
      |1 match {
      |  case 1 =>
      |    if<caret>
      |}
      """

    val resultText =
      """
      |1 match {
      |  case 1 =>
      |    if (<caret>)
      |}
      """

    doCompletionTest(fileText, resultText, "if", '(')
  }

  def testTryBraces() {
    val fileText =
      """
      |1 match {
      |  case 1 =>
      |    try<caret>
      |}
      """

    val resultText =
      """
      |1 match {
      |  case 1 =>
      |    try {<caret>}
      |}
      """

    doCompletionTest(fileText, resultText, "try", '{')
  }

  def testDoWhile() {
    val fileText =
      """
      |do {} whi<caret>
      |1
      """

    val resultText =
      """
      |do {} while (<caret>)
      |1
      """

    doCompletionTest(fileText, resultText, "while", '(')
  }

  def testFilterFinal() {
    val fileText =
      """
      |class Test {
      |  def fina<caret>
      |}
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val lookups = complete(1, CompletionType.SMART)

    Assert.assertTrue(lookups.isEmpty)
  }

  def testFilterImplicit() {
    val fileText =
      """
      |def foo(p: (Int => Int)) {}
      |foo((impl<caret>: Int) => 0)
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val lookups = complete(1, CompletionType.SMART)

    Assert.assertTrue(lookups.isEmpty)
  }
}