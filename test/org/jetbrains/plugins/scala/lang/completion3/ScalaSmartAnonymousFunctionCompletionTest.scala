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
}