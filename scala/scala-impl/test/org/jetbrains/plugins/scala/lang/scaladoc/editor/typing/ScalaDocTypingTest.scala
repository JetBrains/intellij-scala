package org.jetbrains.plugins.scala.lang.scaladoc.editor.typing

import org.jetbrains.plugins.scala.base.EditorActionTestBase

class ScalaDocTypingTest extends EditorActionTestBase {

  def testAdjustStartTagIndent_EmptyComment(): Unit = {
    checkGeneratedTextAfterTypingTextCharByChar(
      s"""/**
         | *     $CARET
         | */
         |class A""".stripMargin,
      s"""/**
         | * @$CARET
         | */
         |class A""".stripMargin,
      "@"
    )
  }

  def testAdjustStartTagIndent_IndentedTooFar(): Unit = {
    checkGeneratedTextAfterTypingTextCharByChar(
      s"""/**
         | * @note text
         | *     $CARET
         | */
         |class A""".stripMargin,
      s"""/**
         | * @note text
         | * @$CARET
         | */
         |class A""".stripMargin,
      "@"
    )
  }

  def testAdjustStartTagIndent_IndentedTooFar_WithNewLinesBetween(): Unit = {
    checkGeneratedTextAfterTypingTextCharByChar(
      s"""/**
         | * @note text
         |
         | *     $CARET
         | */
         |class A""".stripMargin,
      s"""/**
         | * @note text
         |
         | * @$CARET
         | */
         |class A""".stripMargin,
      "@"
    )
  }

  def testAdjustStartTagIndent_Unindented(): Unit = {
    checkGeneratedTextAfterTypingTextCharByChar(
      s"""/**
         | * @note text
         | *$CARET
         | */
         |class A""".stripMargin,
      s"""/**
         | * @note text
         | * @$CARET
         | */
         |class A""".stripMargin,
      "@"
    )
  }

  def testDontAdjustStartTagIndent_WithSomeContentBeforeCaret(): Unit = {
    checkGeneratedTextAfterTypingTextCharByChar(
      s"""/**
         | * @note text
         | *    some text  $CARET
         | */
         |class A""".stripMargin,
      s"""/**
         | * @note text
         | *    some text  @$CARET
         | */
         |class A""".stripMargin,
      "@"
    )
  }

  def testDontAdjustStartTagIndent_WithSomeContentAfterCaret(): Unit = {
    checkGeneratedTextAfterTypingTextCharByChar(
      s"""/**
         | * @note text
         | *    $CARET  some text
         | */
         |class A""".stripMargin,
      s"""/**
         | * @note text
         | *    @$CARET  some text
         | */
         |class A""".stripMargin,
      "@"
    )
  }

  def testDontAdjustStartTagIndent_InsideCodeFragment(): Unit = {
    checkGeneratedTextAfterTypingTextCharByChar(
      s"""/**
         | * {{{
         | *       text
         |         $CARET
         | * }}}
         | */
         |class A
         |""".stripMargin,
      s"""/**
         | * {{{
         | *       text
         |         @$CARET
         | * }}}
         | */
         |class A
         |""".stripMargin,
      "@"
    )
  }


  def testCompleteScaladocOnSpace(): Unit = {
    checkGeneratedTextAfterTyping(
      s"""class X {
         |  /**$CARET
         |  def foo: Unit
         |}
         |""".stripMargin,
      s"""class X {
         |  /** $CARET */
         |  def foo: Unit
         |}
         |""".stripMargin,
      ' '
    )
  }

  def testNotCompleteScaladocOnSpaceIfLineIsNotEmpty(): Unit = {
    checkGeneratedTextAfterTyping(
      s"""class X {
         |  /**$CARET some text
         |  def foo: Unit
         |}
         |""".stripMargin,
      s"""class X {
         |  /** $CARET some text
         |  def foo: Unit
         |}
         |""".stripMargin,
      ' '
    )
  }
}
