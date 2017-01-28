package org.jetbrains.plugins.scala
package lang.xml

import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.plugins.scala.base.EditorActionTestBase

/**
 * User: Dmitry Naydanov
 * Date: 3/5/12
 */
class XmlAttributeQuotesTypingTest extends EditorActionTestBase {

  import CodeInsightTestFixture.CARET_MARKER

  def testQuotesAfterFirstAttribute(): Unit = {
    val text = "class A { val xml = <aaa attr" + CARET_MARKER + "\n}"
    val assumedStub = "class A { val xml = <aaa attr=\"\"\n}"

    checkGeneratedTextAfterTyping(text, assumedStub, '=')
  }

  def testQuotesAfterSecondAttribute(): Unit = {
    val text = "class A { val xml = <aaa attr1=\"blahlbha\" attr2" + CARET_MARKER + "\n}"
    val assumedStub = "class A { val xml = <aaa attr1=\"blahlbha\" attr2=\"\"\n}"

    checkGeneratedTextAfterTyping(text, assumedStub, '=')
  }

  def testSecondQuoteTypingEmptyValue(): Unit = {
    val text = "class A { val xml = <aaa attr=\"" + CARET_MARKER + "\"  }"
    val assumedStub = "class A { val xml = <aaa attr=\"\"" + CARET_MARKER + "  }"

    checkGeneratedTextAfterTyping(text, assumedStub, '\"')
  }

  def testSecondQuoteTypingNonEmptyValue(): Unit = {
    val text = "class A {val xml = <aaa attr=\"blah blah" + CARET_MARKER + "\"\n}"
    val assumedStub = "class A {val xml = <aaa attr=\"blah blah\"" + CARET_MARKER + "\n}"

    checkGeneratedTextAfterTyping(text, assumedStub, '\"')
  }

  def testDeleteFirstQuote1(): Unit = {
    val text = "class A { val xml = <aaa attr=\"" + CARET_MARKER + "\"   }"
    val assumedStub = "class A { val xml = <aaa attr=   }"

    checkGeneratedTextAfterBackspace(text, assumedStub)
  }

  def testDeleteFirstQuote2(): Unit = {
    val text = "class A { val xml = <aaa attr=\"" + CARET_MARKER + "\"></aaa>  }"
    val assumedStub = "class A { val xml = <aaa attr=></aaa>  }"

    checkGeneratedTextAfterBackspace(text, assumedStub)
  }
}
