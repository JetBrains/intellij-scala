package org.jetbrains.plugins.scala.lang.actions.editor.backspace

import org.jetbrains.plugins.scala.ScalaVersion

class MarkdownBackspaceTest extends ScalaBackspaceHandlerBaseTest {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= ScalaVersion.Latest.Scala_3_0

  private def doMultiBackspaceTest(codes: String*): Unit = {
    codes.sliding(2).foreach {
      case Seq(before, after) =>
        doBackspaceTest(before, after)
    }
  }

  def testDeleteAsterisk(): Unit = doMultiBackspaceTest(
    s"""
       |/**
       | * *****$CARET***
       | */
       |""".stripMargin,
    s"""
       |/**
       | * ****$CARET***
       | */
       |""".stripMargin,
    s"""
       |/**
       | * ***$CARET***
       | */
       |""".stripMargin,
    s"""
       |/**
       | * **$CARET**
       | */
       |""".stripMargin,
    s"""
       |/**
       | * *$CARET*
       | */
       |""".stripMargin,
    s"""
       |/**
       | * $CARET
       | */
       |""".stripMargin,
  )

  def testDeletingAsteriskLeft(): Unit = doMultiBackspaceTest(
    s"""
       |/**
       | * **$CARET***
       | */
       |""".stripMargin,
    s"""
       |/**
       | * *$CARET***
       | */
       |""".stripMargin,
    s"""
       |/**
       | * $CARET***
       | */
       |""".stripMargin,
  )

  def testDeletingWikiDocLink(): Unit = doMultiBackspaceTest(
    s"""
       |/**
       | * [[$CARET]]
       | */
       |""".stripMargin,
    s"""
       |/**
       | * [$CARET
       | */
       |""".stripMargin,
    s"""
       |/**
       | * $CARET
       | */
       |""".stripMargin,
  )
}
