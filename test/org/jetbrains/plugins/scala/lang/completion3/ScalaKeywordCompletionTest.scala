package org.jetbrains.plugins.scala
package lang
package completion3

import com.intellij.codeInsight.completion.CompletionType.SMART
import com.intellij.testFramework.EditorTestUtil

/**
  * User: Alexander Podkhalyuzin
  * Date: 04.01.12
  */
class ScalaKeywordCompletionTest extends ScalaCodeInsightTestBase {

  import EditorTestUtil.{CARET_TAG => CARET}

  def testPrivateVal(): Unit = doCompletionTest(
    fileText =
      s"""
         |class A {
         |  private va$CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |class A {
         |  private val $CARET
         |}
      """.stripMargin,
    item = "val"
  )

  def testPrivateThis(): Unit = doCompletionTest(
    fileText =
      s"""
         |class A {
         |  pr$CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |class A {
         |  private[$CARET]
         |}
      """.stripMargin,
    item = "private",
    char = '['
  )

  def testFirstVal(): Unit = doCompletionTest(
    fileText =
      s"""
         |class A {
         |  def foo() {
         |    va${CARET}vv.v
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |class A {
         |  def foo() {
         |    val ${CARET}vv.v
         |  }
         |}
      """.stripMargin,
    item = "val",
    char = ' '
  )

  def testIfAfterCase(): Unit = doCompletionTest(
    fileText =
      s"""
         |1 match {
         |  case a if$CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |1 match {
         |  case a if $CARET
         |}
      """.stripMargin,
    item = "if",
    char = ' '
  )

  def testValUnderCaseClause(): Unit = doCompletionTest(fileText =
    s"""
       |1 match {
       |  case 1 =>
       |    val$CARET
       |}
      """,
    resultText =
      s"""
         |1 match {
         |  case 1 =>
         |    val $CARET
         |}
      """,
    item = "val",
    char = ' '
  )

  def testDefUnderCaseClause(): Unit = doCompletionTest(
    fileText =
      s"""
         |1 match {
         |  case 1 =>
         |    def$CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |1 match {
         |  case 1 =>
         |    def $CARET
         |}
      """.stripMargin,
    item = "def",
    char = ' '
  )

  def testIfParentheses(): Unit = doCompletionTest(
    fileText =
      s"""
         |1 match {
         |  case 1 =>
         |    if$CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |1 match {
         |  case 1 =>
         |    if ($CARET)
         |}
      """.stripMargin,
    item = "if",
    char = '('
  )

  def testTryBraces(): Unit = doCompletionTest(
    fileText =
      s"""
         |1 match {
         |  case 1 =>
         |    try$CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |1 match {
         |  case 1 =>
         |    try {$CARET}
         |}
      """.stripMargin,
    item = "try",
    char = '{'
  )

  def testDoWhile(): Unit = doCompletionTest(
    fileText =
      s"""
         |do {} whi$CARET
         |1
      """.stripMargin,
    resultText =
      s"""
         |do {} while ($CARET)
         |1
      """.stripMargin,
    item = "while",
    char = '('
  )

  def testFilterFinal(): Unit = checkNoCompletion(
    fileText =
      s"""
         |class Test {
         |  def fina$CARET
         |}
      """.stripMargin,
    time = 1,
    completionType = SMART
  ) {
    _ => true
  }

  def testFilterImplicit(): Unit = checkNoCompletion(
    fileText =
      s"""
         |def foo(p: (Int => Int)) {}
         |foo((impl$CARET: Int) => 0)
      """.stripMargin,
    time = 1,
    completionType = SMART
  ) {
    _ => true
  }
}