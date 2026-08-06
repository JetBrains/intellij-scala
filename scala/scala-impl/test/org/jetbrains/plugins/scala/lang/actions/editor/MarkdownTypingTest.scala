package org.jetbrains.plugins.scala.lang.actions.editor

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.EditorActionTestBase

class MarkdownTypingTest extends EditorActionTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version.isScala3

  private def doTest(typedChar: Char, wrapInDocComment: Boolean = true)(before: String, after: String): Unit = {
    def wrap(s: String) =
      if (wrapInDocComment)
        """/**
          | * <>
          | */
          |""".stripMargin.replace("<>", s.trim.linesIterator.mkString("\n * "))
      else
        s.trim

    checkGeneratedTextAfterTyping(wrap(before), wrap(after), typedChar, defaultFileName)
  }

  def testStar(): Unit = {
    doTest('*')(s"$CARET", s"*$CARET*")
    doTest('*')(s"*$CARET*", s"**$CARET**")
    doTest('*')(s"**$CARET**", s"***$CARET***")

    doTest('_')(s"$CARET", s"_${CARET}_")
    doTest('_')(s"_${CARET}_", s"__${CARET}__")
    doTest('_')(s"__${CARET}__", s"___${CARET}___")
  }

  def testStarBeforeAfterAsterisk(): Unit = {
    doTest('*')(s"$CARET*", s"*$CARET*")
    doTest('*')(s"*$CARET", s"**$CARET")
    doTest('*')(s"*$CARET**", s"**$CARET**")
    doTest('*')(s"**$CARET*", s"***$CARET*")

    doTest('_')(s"${CARET}_", s"_${CARET}_")
    doTest('_')(s"_${CARET}", s"__${CARET}")
    doTest('_')(s"_${CARET}__", s"__${CARET}__")
    doTest('_')(s"__${CARET}_", s"___${CARET}_")
  }

  def testStarBeforeLeadingAsterisk(): Unit = {
    doTest('*', wrapInDocComment = false)(
      s"""
         |/**
         | $CARET*
         | */
         |""".stripMargin,
      s"""
         |/**
         | *$CARET*
         | */
         |""".stripMargin
    )

    doTest('*', wrapInDocComment = false)(
      s"""
         |/**
         | $CARET *
         | */
         |""".stripMargin,
      s"""
         |/**
         | *$CARET *
         | */
         |""".stripMargin
    )
  }

  def testStarOnLineWithoutLeadingAstesik(): Unit =
    doTest('*', wrapInDocComment = false)(
      s"""
         |/**
         | $CARET
         | */
         |""".stripMargin,
      s"""
         |/**
         | *$CARET
         | */
         |""".stripMargin
    )

  def testStarInDocCommentBeginning(): Unit = {
    doTest('*', wrapInDocComment = false)(
      s"""
         |/$CARET**
         | */
         |""".stripMargin,
      s"""
         |/*$CARET**
         | */
         |""".stripMargin
    )

    doTest('*', wrapInDocComment = false)(
      s"""
         |/*$CARET*
         | */
         |""".stripMargin,
      s"""
         |/**$CARET*
         | */
         |""".stripMargin
    )
  }

  def testWikidocLink(): Unit = {
    doTest('[')(s"$CARET", s"[$CARET")
    doTest('[')(s"[$CARET", s"[[$CARET]]")
  }
}
