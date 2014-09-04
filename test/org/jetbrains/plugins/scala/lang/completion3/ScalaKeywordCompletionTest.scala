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
      """.stripMargin('|').replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.BASIC)

    val resultText =
      """
      |class A {
      |  private val <caret>
      |}
      """.stripMargin('|').replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(le => le.getLookupString == "val").get)
    checkResultByText(resultText)
  }

  def testPrivateThis() {
    val fileText =
      """
        |class A {
        |  pr<caret>
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.BASIC)

    val resultText =
      """
        |class A {
        |  private[<caret>]
        |}
      """.stripMargin('|').replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(le => le.getLookupString == "private").get, '[')
    checkResultByText(resultText)
  }

  def testFirstVal() {
    val fileText =
      """
      |class A {
      |  def foo() {
      |    va<caret>vv.v
      |  }
      |}
      """.stripMargin('|').replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.BASIC)

    val resultText =
      """
      |class A {
      |  def foo() {
      |    val <caret>vv.v
      |  }
      |}
      """.stripMargin('|').replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(le => le.getLookupString == "val").get, ' ')
    checkResultByText(resultText)
  }

  def testIfAfterCase() {
    val fileText =
      """
      |1 match {
      |  case a if<caret>
      |}
      """.stripMargin('|').replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.BASIC)

    val resultText =
      """
      |1 match {
      |  case a if <caret>
      |}
      """.stripMargin('|').replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(le => le.getLookupString == "if").get, ' ')
    checkResultByText(resultText)
  }

  def testValUnderCaseClause() {
    val fileText =
      """
      |1 match {
      |  case 1 =>
      |    val<caret>
      |}
      """.stripMargin('|').replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.BASIC)

    val resultText =
      """
      |1 match {
      |  case 1 =>
      |    val <caret>
      |}
      """.stripMargin('|').replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(le => le.getLookupString == "val").get, ' ')
    checkResultByText(resultText)
  }

  def testDefUnderCaseClause() {
    val fileText =
      """
      |1 match {
      |  case 1 =>
      |    def<caret>
      |}
      """.stripMargin('|').replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.BASIC)

    val resultText =
      """
      |1 match {
      |  case 1 =>
      |    def <caret>
      |}
      """.stripMargin('|').replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(le => le.getLookupString == "def").get, ' ')
    checkResultByText(resultText)
  }

  def testIfParentheses() {
    val fileText =
      """
      |1 match {
      |  case 1 =>
      |    if<caret>
      |}
      """.stripMargin('|').replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.BASIC)

    val resultText =
      """
      |1 match {
      |  case 1 =>
      |    if (<caret>)
      |}
      """.stripMargin('|').replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(le => le.getLookupString == "if").get, '(')
    checkResultByText(resultText)
  }

  def testTryBraces() {
    val fileText =
      """
      |1 match {
      |  case 1 =>
      |    try<caret>
      |}
      """.stripMargin('|').replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.BASIC)

    val resultText =
      """
      |1 match {
      |  case 1 =>
      |    try {<caret>}
      |}
      """.stripMargin('|').replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(le => le.getLookupString == "try").get, '{')
    checkResultByText(resultText)
  }

  def testDoWhile() {
    val fileText =
      """
      |do {} whi<caret>
      |1
      """.stripMargin('|').replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.BASIC)

    val resultText =
      """
      |do {} while (<caret>)
      |1
      """.stripMargin('|').replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(le => le.getLookupString == "while").get, '(')
    checkResultByText(resultText)
  }

  def testFilterFinal() {
    val fileText =
      """
      |class Test {
      |  def fina<caret>
      |}
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.SMART)

    Assert.assertNull(activeLookup)
  }

  def testFilterImplicit() {
    val fileText =
      """
      |def foo(p: (Int => Int)) {}
      |foo((impl<caret>: Int) => 0)
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, _) = complete(1, CompletionType.SMART)

    Assert.assertNull(activeLookup)
  }
}