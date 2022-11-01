package org.jetbrains.plugins.scala.lang.actions.editor

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.plugins.scala.base.EditorActionTestBase
import org.jetbrains.plugins.scala.util.RevertableChange

class InterpolatedStringTypingTest extends EditorActionTestBase {

  import CodeInsightTestFixture.CARET_MARKER

  def testSimpleStringTypingOpeningQuote(): Unit = {
    val text = s"""class A { val a = s$CARET_MARKER }"""
    val assumedStub = s"""class A { val a = s"$CARET_MARKER" }"""

    checkGeneratedTextAfterTyping(text, assumedStub, '\"')
  }

  def testSimpleStringTypingClosingQuote(): Unit = {
    val text = s"""class A { val a = s"$CARET_MARKER" }"""
    val assumedStub = s"""class A { val a = s""$CARET_MARKER }"""

    checkGeneratedTextAfterTyping(text, assumedStub, '\"')
  }

  def testSimpleQuasiQuoteTypingOpeningQuote(): Unit = {
    val text = s"""class A { val a = q$CARET_MARKER }"""
    val assumedStub = s"""class A { val a = q"$CARET_MARKER" }"""

    checkGeneratedTextAfterTyping(text, assumedStub, '\"')
  }

  def testSimpleQuasiQuoteTypingClosingQuote(): Unit = {
    val text = s"""class A { val a = q"$CARET_MARKER" }"""
    val assumedStub = """class A { val a = q""""" + CARET_MARKER + " }"

    checkGeneratedTextAfterTyping(text, assumedStub, '\"')
  }

  def testMultilineStringTypingOpeningQuote(): Unit = {
    val text = s"""class A { val a = f""$CARET_MARKER }"""
    val assumedStub = s"""class A { val a = f""\"$CARET_MARKER""\" }"""

    checkGeneratedTextAfterTyping(text, assumedStub, '\"')
  }

  def testMultilineQuasiQuoteOpeningQuote(): Unit = {
    val text = s"""class A { val a = q""$CARET_MARKER }"""
    val assumedStub = s"""class A { val a = q""\"$CARET_MARKER""\" }"""

    checkGeneratedTextAfterTyping(text, assumedStub, '\"')
  }

  def testMultilineStringClosingQuote1(): Unit = {
    val text = s"""class A { val a = s""\"blah blah$CARET_MARKER""\" }"""
    val assumedStub = s"""class A { val a = s""\"blah blah"$CARET_MARKER"" }"""

    checkGeneratedTextAfterTyping(text, assumedStub, '\"')
  }

  def testMultilineStringClosingQuote2(): Unit = {
    val text = s"""class A { val a = s""\"blah blah""$CARET_MARKER" }"""
    val assumedStub = s"""class A { val a = s""\"blah blah""\"$CARET_MARKER }"""

    checkGeneratedTextAfterTyping(text, assumedStub, '\"')
  }

  def testMultilineQuasiQuoteClosingQuote1(): Unit = {
    val text = s"""class A { val a = q""\"blah blah$CARET_MARKER""\" }"""
    val assumedStub = s"""class A { val a = q""\"blah blah"$CARET_MARKER"" }"""

    checkGeneratedTextAfterTyping(text, assumedStub, '\"')
  }

  def testMultilineQuasiQuoteClosingQuote2(): Unit = {
    val text = s"""class A { val a = q""\"blah blah""$CARET_MARKER" }"""
    val assumedStub = s"""class A { val a = q""\"blah blah""\"$CARET_MARKER }"""

    checkGeneratedTextAfterTyping(text, assumedStub, '\"')
  }

  def testSimpleStringBraceTyped(): Unit = {
    val text = s"""class A { val a = s"blah blah $$$CARET_MARKER" }"""
    val assumedStub = s"""class A { val a = s"blah blah $${$CARET_MARKER}" }"""

    checkGeneratedTextAfterTyping(text, assumedStub, '{')
  }

  def testMultiLineStringBraceTyped(): Unit = {
    val text = s"""class A { val a = f""\"blah blah $$$CARET_MARKER blah blah""\"}"""
    val assumedStub = s"""class A { val a = f""\"blah blah $${$CARET_MARKER} blah blah""\"}"""

    checkGeneratedTextAfterTyping(text, assumedStub, '{')
  }

  private def withModifiedCodeInsightSettings[T](
    get: CodeInsightSettings => T,
    set: (CodeInsightSettings, T) => Unit,
    value: T
  ): RevertableChange = new RevertableChange {
    private def instance = CodeInsightSettings.getInstance
    private var before: Option[T] = None

    override def applyChange(): Unit = {
      before = Some(get(instance))
      set(instance, value)
    }

    override def revertChange(): Unit =
      before.foreach(set(instance, _))
  }

  private def withAutoInsertPairBracket(value: Boolean): RevertableChange =
    withModifiedCodeInsightSettings[Boolean](
      _.AUTOINSERT_PAIR_BRACKET,
      _.AUTOINSERT_PAIR_BRACKET = _,
      value
    )

  def testInsertBrace(): Unit = withAutoInsertPairBracket(true) {
    val text = s""" val a = s"($$$CARET_MARKER)" """
    val assumed = s""" val a = s"($${$CARET_MARKER})" """

    checkGeneratedTextAfterTyping(text, assumed, '{')
  }

  def testDontInsertBrace(): Unit = withAutoInsertPairBracket(false) {
    val text = s""" val a = s"($$$CARET_MARKER)" """
    val assumed = s""" val a = s"($${$CARET_MARKER)" """

    checkGeneratedTextAfterTyping(text, assumed, '{')
  }

  def testInsertBraceInvalidCode(): Unit = withAutoInsertPairBracket(true) {

    val text = s""" val a = s"blah-blah $$$CARET_MARKER """
    val assumed = s""" val a = s"blah-blah $${$CARET_MARKER} """

    checkGeneratedTextAfterTyping(text, assumed, '{')
  }

  def testDontInsertBraceInvalidCode(): Unit = withAutoInsertPairBracket(false) {
    val text = s""" val a = s"blah-blah $$$CARET_MARKER """
    val assumed = s""" val a = s"blah-blah $${$CARET_MARKER """

    checkGeneratedTextAfterTyping(text, assumed, '{')
  }
}
