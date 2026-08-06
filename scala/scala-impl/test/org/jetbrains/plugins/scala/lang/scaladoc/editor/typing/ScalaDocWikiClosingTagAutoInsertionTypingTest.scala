package org.jetbrains.plugins.scala.lang.scaladoc.editor.typing

import org.jetbrains.plugins.scala.base.EditorActionTestBase

class ScalaDocWikiClosingTagAutoInsertionTypingTest extends EditorActionTestBase {

  def testCodeLink(): Unit =
    checkGeneratedTextAfterTypingTextCharByChar(
      s"/** $CARET */",
      s"/** [[$CARET]] */",
      "[["
    )

  def testCodeLink_1(): Unit =
    checkGeneratedTextAfterTypingTextCharByChar(
      s"/** [$CARET */",
      s"/** [[$CARET]] */",
      "["
    )

  def testCodeLink_MultipleSquareBrackets(): Unit = {
    checkGeneratedTextAfterTypingTextCharByChar(
      s"""/** $CARET */ class A""",
      s"""/** [[[[[$CARET]] */ class A""",
      "[[[[["
    )
  }

  def testInnerCodeFragment(): Unit =
    checkGeneratedTextAfterTyping(
      s"/** {{$CARET */",
      s"/** {{{$CARET}}} */",
      '{'
    )

  def testInnerCodeFragment_1(): Unit =
    checkGeneratedTextAfterTypingTextCharByChar(
      s"/** $CARET */",
      s"/** {{{$CARET}}} */",
      "{{{"
    )

  def testInnerCodeFragment_NotEnoughChars_JustInsertText(): Unit = {
    checkGeneratedTextAfterTypingTextCharByChar(
      s"""/** $CARET */ class A""",
      s"""/** {{$CARET */ class A""",
      "{{"
    )
  }

  def testMonospace(): Unit =
    checkGeneratedTextAfterTyping(
      s"/** $CARET */",
      s"/** `$CARET` */",
      '`'
    )

  def testSuperscript(): Unit =
    checkGeneratedTextAfterTyping(
      s"/** $CARET */",
      s"/** ^$CARET^ */",
      '^'
    )

  def testSubscript(): Unit =
    checkGeneratedTextAfterTyping(
      s"/** ,$CARET */",
      s"/** ,,$CARET,, */",
      ','
    )

  def testItalic(): Unit = {
    checkGeneratedTextAfterTypingTextCharByChar(
      s"""/** $CARET */ class A""",
      s"""/** ''$CARET'' */ class A""",
      "''"
    )
  }

  def testBoldSyntax_ExtendedInsideItalic(): Unit =
    checkGeneratedTextAfterTypingTextCharByChar(
      s"/** ''$CARET'' */",
      s"/** '''$CARET''' */",
      "'"
    )

  def testBoldSyntax_ExtendedInsideItalic_1(): Unit =
    checkGeneratedTextAfterTypingTextCharByChar(
      s"""/** $CARET */ class A""",
      s"""/** '''$CARET''' */ class A""",
      "'''"
    )

  def testWikiSyntax_3_SingleQuote_WithText_CloseTag_Bold(): Unit = {
    checkGeneratedTextAfterTypingTextCharByChar(
      s"""/** $CARET */ class A""",
      s"""/** '''text$CARET''' */ class A""",
      "'''text"
    )
  }

  def testBoldSyntax_AfterTwoSingleQuotes(): Unit = {
    checkGeneratedTextAfterTypingTextCharByChar(
      s"""/** ''$CARET */ class A""",
      s"""/** '''$CARET''' */ class A""",
      "'"
    )
  }

  def tesSingleQuote_JustInsertSymbol(): Unit = {
    checkGeneratedTextAfterTypingTextCharByChar(
      s"""/** $CARET */ class A""",
      s"""/** '$CARET */ class A""",
      "'"
    )
  }

  def testDontInsertClosingTag_InsideCodeFragment(): Unit = {
    checkGeneratedTextAfterTypingTextCharByChar(
      s"""/** {{{ $CARET }}} */ class A""",
      s"""/** {{{ '''[[`$CARET }}} */ class A""",
      "'''[[`"
    )
  }
}
