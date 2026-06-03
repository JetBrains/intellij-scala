package org.jetbrains.plugins.scala.lang.actions.editor.backspace

abstract class ScalaDocLineDeletionBackspaceHandlerBase extends ScalaBackspaceHandlerBaseTest

class ScalaDocLineDeletionBackspaceHandler_StartsOnSecondLine extends ScalaDocLineDeletionBackspaceHandlerBase {
  def test_indented(): Unit =
    doBackspaceTest(
      s"""
         |/**
         | * - A list
         | *   ${CARET}abc
         | */
         |""".stripMargin,
      s"""
         |/**
         | * - A list
         | * ${CARET}abc
         | */
         |""".stripMargin,
    )

  def test_delete_space_after_asterisk(): Unit =
    doBackspaceTest(
      s"""
         |/**
         | * - A list
         | * ${CARET}abc
         | */
         |""".stripMargin,
      s"""
         |/**
         | * - A list${CARET}abc
         | */
         |""".stripMargin,
    )

  def test_delete_asterisk(): Unit =
    doBackspaceTest(
      s"""
         |/**
         | * - A list
         | *${CARET}abc
         | */
         |""".stripMargin,
      s"""
         |/**
         | * - A list${CARET}abc
         | */
         |""".stripMargin,
    )

  def test_delete_asterisk_after_asterisk(): Unit =
    doBackspaceTest(
      s"""
         |/**
         | * - A list
         | **${CARET}abc
         | */
         |""".stripMargin,
      s"""
         |/**
         | * - A list
         | *${CARET}abc
         | */
         |""".stripMargin,
    )
}


class ScalaDocLineDeletionBackspaceHandler_StartsOnFirstLine extends ScalaDocLineDeletionBackspaceHandlerBase {
  def test_indented(): Unit =
    doBackspaceTest(
      s"""
         |/** - A list
         | *    ${CARET}abc
         | */
         |""".stripMargin,
      s"""
         |/** - A list
         | *  ${CARET}abc
         | */
         |""".stripMargin,
    )

  def test_delete_space_after_space_after_asterisk(): Unit =
    doBackspaceTest(
      s"""
         |/** - A list
         | *  ${CARET}abc
         | */
         |""".stripMargin,
      s"""
         |/** - A list${CARET}abc
         | */
         |""".stripMargin,
    )

  def test_delete_space_after_asterisk(): Unit =
    doBackspaceTest(
      s"""
         |/** - A list
         | * ${CARET}abc
         | */
         |""".stripMargin,
      s"""
         |/** - A list${CARET}abc
         | */
         |""".stripMargin,
    )

  def test_delete_asterisk(): Unit =
    doBackspaceTest(
      s"""
         |/** - A list
         | *${CARET}abc
         | */
         |""".stripMargin,
      s"""
         |/** - A list${CARET}abc
         | */
         |""".stripMargin,
    )

  def test_delete_asterisk_after_asterisk(): Unit =
    doBackspaceTest(
      s"""
         |/** - A list
         | **${CARET}abc
         | */
         |""".stripMargin,
      s"""
         |/** - A list
         | *${CARET}abc
         | */
         |""".stripMargin,
    )

  def test_delete_before_comment_end(): Unit =
    doTest(
      s"""
         |/**
         | * Test Comment
         | * $CARET*/
         |""".stripMargin,
      s"""
         |/**
         | * Test Comment
         | *$CARET*/
         |""".stripMargin,
      s"""
         |/**
         | * Test Comment
         | $CARET*/
         |""".stripMargin,
      s"""
         |/**
         | * Test Comment $CARET*/
         |""".stripMargin,
    )
}
