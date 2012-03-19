package org.jetbrains.plugins.scala.lang.completion3

import com.intellij.codeInsight.completion.CompletionType

/**
 * @author Alexander Podkhalyuzin
 */

class ScalaSmartAnonymousFunctionCompletionTest extends ScalaCompletionTestBase {
  def testAbstractTypeInfoFromFirstClause() {
    val fileText =
"""
def foo[T](x: T)(y: T => Int) = 1
foo(2)(<caret>)
""".replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, prefix) = complete(2, CompletionType.SMART)

    val resultText =
"""
def foo[T](x: T)(y: T => Int) = 1
foo(2)((i: Int) =><caret>)
""".replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(le => le.getLookupString == "").get)
    checkResultByText(resultText)
  }

  def testFewParams() {
    val fileText =
      """
      |def foo(c: (Int, Int, Int, Int) => Int) = 1
      |foo(<caret>)
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, prefix) = complete(2, CompletionType.SMART)

    val resultText =
      """
      |def foo(c: (Int, Int, Int, Int) => Int) = 1
      |foo((i: Int, i0: Int, i1: Int, i2: Int) =><caret>)
      """.stripMargin.replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(le => le.getLookupString == "").get)
    checkResultByText(resultText)
  }

  def testFewParamsDifferent() {
    val fileText =
      """
      |def foo(x: (Int, String, Int, String) => Int) = 1
      |foo(<caret>)
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, prefix) = complete(2, CompletionType.SMART)

    val resultText =
      """
      |def foo(x: (Int, String, Int, String) => Int) = 1
      |foo((i: Int, s: String, i0: Int, s0: String) =><caret>)
      """.stripMargin.replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(le => le.getLookupString == "").get)
    checkResultByText(resultText)
  }

  def testAbstractTypeInfo() {
    val fileText =
      """
      |def foo[T](x: (T, String) => String) = 1
      |foo(<caret>)
      """.stripMargin.replaceAll("\r", "").trim()
    configureFromFileTextAdapter("dummy.scala", fileText)
    val (activeLookup, prefix) = complete(2, CompletionType.SMART)

    val resultText =
      """
      |def foo[T](x: (T, String) => String) = 1
      |foo((value: T, s: String) =><caret>)
      """.stripMargin.replaceAll("\r", "").trim()

    completeLookupItem(activeLookup.find(le => le.getLookupString == "").get)
    checkResultByText(resultText)
  }
}