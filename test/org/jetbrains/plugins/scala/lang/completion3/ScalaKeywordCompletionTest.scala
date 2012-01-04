package org.jetbrains.plugins.scala.lang.completion3

import com.intellij.codeInsight.completion.CompletionType

/**
 * User: Alexander Podkhalyuzin
 * Date: 04.01.12
 */

class ScalaKeywordCompletionTest extends ScalaCompletionTestBase {
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
}