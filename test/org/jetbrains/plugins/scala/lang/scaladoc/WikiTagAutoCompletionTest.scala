package org.jetbrains.plugins.scala
package lang.scaladoc

import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.plugins.scala.base.EditorActionTestBase

/**
 * User: Dmitry Naydanov
 * Date: 2/25/12
 */
class WikiTagAutoCompletionTest extends EditorActionTestBase {

  import CodeInsightTestFixture.CARET_MARKER

  def testCodeLinkAC(): Unit = {
    val text = "/** [" + CARET_MARKER + " */"
    val assumedStub = "/** [[]] */"
    checkGeneratedTextAfterTyping(text, assumedStub, '[')
  }

  def testInnerCodeAC(): Unit = {
    val text = "/** {{" + CARET_MARKER + " */"
    val assumedStub = "/** {{{}}} */"
    checkGeneratedTextAfterTyping(text, assumedStub, '{')
  }

  def testMonospaceAC(): Unit = {
    val text = "/** " + CARET_MARKER + " */"
    val assumedStub = "/** `` */"
    checkGeneratedTextAfterTyping(text, assumedStub, '`')
  }

  def testSuperscriptAC(): Unit = {
    val text = "/** " + CARET_MARKER + " */"
    val assumedStub = "/** ^^ */"
    checkGeneratedTextAfterTyping(text, assumedStub, '^')
  }

  def testSubscriptAC(): Unit = {
    val text = "/** ," + CARET_MARKER + " */"
    val assumedStub = "/** ,,,, */"
    checkGeneratedTextAfterTyping(text, assumedStub, ',')
  }

  def testBoldSimpleAC(): Unit = {
    val text = "/** ''" + CARET_MARKER + "'' */"
    val assumedStub = "/** '''''' */"
    checkGeneratedTextAfterTyping(text, assumedStub, '\'')
  }
}
