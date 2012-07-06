package org.jetbrains.plugins.scala
package lang.actions.editor

import lang.completion3.ScalaLightCodeInsightFixtureTestAdapter

/**
 * User: Dmitry Naydanov
 * Date: 3/31/12
 */

class InterpolatedStringTypingTest extends ScalaLightCodeInsightFixtureTestAdapter {
  import ScalaLightCodeInsightFixtureTestAdapter.CARET_MARKER

  def testSimpleStringTypingOpeningQuote() {
    val text = "class A { val a = s" + CARET_MARKER + " }"
    val assumedStub = "class A { val a = s\"" + CARET_MARKER + "\" }"

    checkGeneratedTextAfterTyping(text, assumedStub, '\"')
  }

  def testSimpleStringTypingClosingQuote() {
    val text = "class A { val a = s\"" + CARET_MARKER + "\" }"
    val assumedStub = "class A { val a = s\"\"" + CARET_MARKER + " }"

    checkGeneratedTextAfterTyping(text, assumedStub, '\"')
  }

  def testMultilineStringTypingOpeningQuote() {
    val text = "class A { val a = f\"\"" + CARET_MARKER + " }"
    val assumedStub = "class A { val a = f\"\"\"" + CARET_MARKER + "\"\"\" }"

    checkGeneratedTextAfterTyping(text, assumedStub, '\"')
  }

  def testMultilingStringClosingQuote1() {
    val text = "class A { val a = s\"\"\"blah blah" + CARET_MARKER + "\"\"\" }"
    val assumedStub = "class A { val a = s\"\"\"blah blah\"" + CARET_MARKER + "\"\" }"

    checkGeneratedTextAfterTyping(text, assumedStub, '\"')
  }

  def testMultilineStringClosingQuote2() {
    val text = "class A { val a = s\"\"\"blah blah\"\"" + CARET_MARKER + "\" }"
    val assumedStub = "class A { val a = s\"\"\"blah blah\"\"\"" + CARET_MARKER + " }"

    checkGeneratedTextAfterTyping(text, assumedStub, '\"')
  }
  
  def testSimpleStringBraceTyped() {
    val text = "class A { val a = s\"blah blah $" + CARET_MARKER + "\" }"
    val assumedStub = "class A { val a = s\"blah blah ${" + CARET_MARKER + "}\" }"
    
    checkGeneratedTextAfterTyping(text, assumedStub, '{')
  }
  
  def testMultiLineStringBraceTyped() {
    val text = "class A { val a = f\"\"\"blah blah $" + CARET_MARKER + " blah blah\"\"\"}"
    val assumedStub = "class A { val a = f\"\"\"blah blah ${" + CARET_MARKER + "} blah blah\"\"\"}"

    checkGeneratedTextAfterTyping(text, assumedStub, '{')
  }
}
