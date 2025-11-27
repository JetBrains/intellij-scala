package org.jetbrains.plugins.scala.lang.actions.editor.backspace

class ScalaDocLineDeletionBackspaceHandler extends ScalaBackspaceHandlerBaseTest {
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
