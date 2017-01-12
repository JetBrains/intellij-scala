package org.jetbrains.plugins.scala
package lang.scaladoc

import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

/**
 * User: Dmitry Naydanov
 * Date: 2/25/12
 */
class WikiTagAutoCompletionTest extends ScalaLightCodeInsightFixtureTestAdapter {

  import CodeInsightTestFixture.CARET_MARKER

  def testCodeLinkAC() {
    val text = "/** [" + CARET_MARKER + " */"
    val assumedStub = "/** [[]] */"
    checkGeneratedTextAfterTyping(text, assumedStub, '[')
  }

  def testInnerCodeAC() {
    val text = "/** {{" + CARET_MARKER + " */"
    val assumedStub = "/** {{{}}} */"
    checkGeneratedTextAfterTyping(text, assumedStub, '{')
  }

  def testMonospaceAC() {
    val text = "/** " + CARET_MARKER + " */"
    val assumedStub = "/** `` */"
    checkGeneratedTextAfterTyping(text, assumedStub, '`')
  }

  def testSuperscriptAC() {
    val text = "/** " + CARET_MARKER + " */"
    val assumedStub = "/** ^^ */"
    checkGeneratedTextAfterTyping(text, assumedStub, '^')
  }

  def testSubscriptAC() {
    val text = "/** ," + CARET_MARKER + " */"
    val assumedStub = "/** ,,,, */"
    checkGeneratedTextAfterTyping(text, assumedStub, ',')
  }

  def testBoldSimpleAC() {
    val text = "/** ''" + CARET_MARKER + "'' */"
    val assumedStub = "/** '''''' */"
    checkGeneratedTextAfterTyping(text, assumedStub, '\'')
  }
}
