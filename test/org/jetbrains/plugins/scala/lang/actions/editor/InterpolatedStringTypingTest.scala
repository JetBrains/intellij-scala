package org.jetbrains.plugins.scala
package lang.actions.editor

import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.plugins.scala.base.EditorActionTestBase

/**
 * User: Dmitry Naydanov
 * Date: 3/31/12
 */
class InterpolatedStringTypingTest extends EditorActionTestBase {

  import CodeInsightTestFixture.CARET_MARKER

  def testSimpleStringTypingOpeningQuote(): Unit = {
    val text = "class A { val a = s" + CARET_MARKER + " }"
    val assumedStub = "class A { val a = s\"" + CARET_MARKER + "\" }"

    checkGeneratedTextAfterTyping(text, assumedStub, '\"')
  }

  def testSimpleStringTypingClosingQuote(): Unit = {
    val text = "class A { val a = s\"" + CARET_MARKER + "\" }"
    val assumedStub = "class A { val a = s\"\"" + CARET_MARKER + " }"

    checkGeneratedTextAfterTyping(text, assumedStub, '\"')
  }

  def testSimpleQuasiQuoteTypingOpeningQuote(): Unit = {
    val text = "class A { val a = q" + CARET_MARKER + " }"
    val assumedStub = "class A { val a = q\"" + CARET_MARKER + "\" }"

    checkGeneratedTextAfterTyping(text, assumedStub, '\"')
  }

  def testSimpleQuasiQuoteTypingClosingQuote(): Unit = {
    val text = "class A { val a = q\"" + CARET_MARKER + "\" }"
    val assumedStub = "class A { val a = q\"\"" + CARET_MARKER + " }"

    checkGeneratedTextAfterTyping(text, assumedStub, '\"')
  }

  def testMultilineStringTypingOpeningQuote(): Unit = {
    val text = "class A { val a = f\"\"" + CARET_MARKER + " }"
    val assumedStub = "class A { val a = f\"\"\"" + CARET_MARKER + "\"\"\" }"

    checkGeneratedTextAfterTyping(text, assumedStub, '\"')
  }

  def testMultilineQuasiQuoteOpeningQuote(): Unit = {
    val text = "class A { val a = q\"\"" + CARET_MARKER + " }"
    val assumedStub = "class A { val a = q\"\"\"" + CARET_MARKER + "\"\"\" }"

    checkGeneratedTextAfterTyping(text, assumedStub, '\"')
  }

  def testMultilingStringClosingQuote1(): Unit = {
    val text = "class A { val a = s\"\"\"blah blah" + CARET_MARKER + "\"\"\" }"
    val assumedStub = "class A { val a = s\"\"\"blah blah\"" + CARET_MARKER + "\"\" }"

    checkGeneratedTextAfterTyping(text, assumedStub, '\"')
  }

  def testMultilineStringClosingQuote2(): Unit = {
    val text = "class A { val a = s\"\"\"blah blah\"\"" + CARET_MARKER + "\" }"
    val assumedStub = "class A { val a = s\"\"\"blah blah\"\"\"" + CARET_MARKER + " }"

    checkGeneratedTextAfterTyping(text, assumedStub, '\"')
  }

  def testMultilineQuasiQuoteClosingQuote1(): Unit = {
    val text = "class A { val a = q\"\"\"blah blah" + CARET_MARKER + "\"\"\" }"
    val assumedStub = "class A { val a = q\"\"\"blah blah\"" + CARET_MARKER + "\"\" }"

    checkGeneratedTextAfterTyping(text, assumedStub, '\"')
  }

  def testMultilineQuasiQuoteClosingQuote2(): Unit = {
    val text = "class A { val a = q\"\"\"blah blah\"\"" + CARET_MARKER + "\" }"
    val assumedStub = "class A { val a = q\"\"\"blah blah\"\"\"" + CARET_MARKER + " }"

    checkGeneratedTextAfterTyping(text, assumedStub, '\"')
  }

  def testSimpleStringBraceTyped(): Unit = {
    val text = "class A { val a = s\"blah blah $" + CARET_MARKER + "\" }"
    val assumedStub = "class A { val a = s\"blah blah ${" + CARET_MARKER + "}\" }"
    
    checkGeneratedTextAfterTyping(text, assumedStub, '{')
  }

  def testMultiLineStringBraceTyped(): Unit = {
    val text = "class A { val a = f\"\"\"blah blah $" + CARET_MARKER + " blah blah\"\"\"}"
    val assumedStub = "class A { val a = f\"\"\"blah blah ${" + CARET_MARKER + "} blah blah\"\"\"}"

    checkGeneratedTextAfterTyping(text, assumedStub, '{')
  }

  def testInsertBrace(): Unit = {
    val text = s""" val a = s"($$$CARET_MARKER)" """
    val assumed = s""" val a = s"($${$CARET_MARKER})" """

    checkGeneratedTextAfterTyping(text, assumed, '{')
  }

  def testInsertBraceInvalidCode(): Unit = {
    val text = s""" val a = s"blah-blah $$$CARET_MARKER """
    val assumed = s""" val a = s"blah-blah $${$CARET_MARKER} """

    checkGeneratedTextAfterTyping(text, assumed, '{')
  }
}
