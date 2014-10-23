package org.jetbrains.plugins.scala
package lang.xml

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

/**
 * User: Dmitry Naydanov
 * Date: 3/5/12
 */

class XmlAttributeQuotesTypingTest extends ScalaLightCodeInsightFixtureTestAdapter {

  def testQuotesAfterFirstAttribute() {
    val text = "class A { val xml = <aaa attr" + CARET_MARKER + "\n}"
    val assumedStub = "class A { val xml = <aaa attr=\"\"\n}"

    checkGeneratedTextAfterTyping(text, assumedStub, '=')
  }

  def testQuotesAfterSecondAttribute() {
    val text = "class A { val xml = <aaa attr1=\"blahlbha\" attr2" + CARET_MARKER + "\n}"
    val assumedStub = "class A { val xml = <aaa attr1=\"blahlbha\" attr2=\"\"\n}"

    checkGeneratedTextAfterTyping(text, assumedStub, '=')
  }

  def testSecondQuoteTypingEmptyValue() {
    val text = "class A { val xml = <aaa attr=\"" + CARET_MARKER + "\"  }"
    val assumedStub = "class A { val xml = <aaa attr=\"\"" + CARET_MARKER + "  }"

    checkGeneratedTextAfterTyping(text, assumedStub, '\"')
  }

  def testSeconQuoteTypingNonEmptyValue() {
    val text = "class A {val xml = <aaa attr=\"blah blah" + CARET_MARKER + "\"\n}"
    val assumedStub = "class A {val xml = <aaa attr=\"blah blah\"" + CARET_MARKER + "\n}"

    checkGeneratedTextAfterTyping(text, assumedStub, '\"')
  }

  def testDeleteFirstQuote1() {
    val text = "class A { val xml = <aaa attr=\"" + CARET_MARKER + "\"   }"
    val assumedStub = "class A { val xml = <aaa attr=   }"

    checkGeneratedTextAfterBackspace(text, assumedStub)
  }

  def testDeleteFirstQuote2() {
    val text = "class A { val xml = <aaa attr=\"" + CARET_MARKER + "\"></aaa>  }"
    val assumedStub = "class A { val xml = <aaa attr=></aaa>  }"

    checkGeneratedTextAfterBackspace(text, assumedStub)
  }
}
