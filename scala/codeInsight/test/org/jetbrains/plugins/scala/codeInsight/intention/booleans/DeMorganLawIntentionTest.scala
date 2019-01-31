package org.jetbrains.plugins.scala
package codeInsight
package intention
package booleans

import com.intellij.testFramework.EditorTestUtil

/**
  * @author Ksenia.Sautina
  * @since 5/12/12
  */
class DeMorganLawIntentionTest extends intentions.ScalaIntentionTestBase {

  import EditorTestUtil.{CARET_TAG => CARET}

  override def familyName = DeMorganLawIntention.FamilyName

  def test1(): Unit = {
    val text = s"if (a |$CARET| b) {}"
    val resultText = s"if (!(!a &$CARET& !b)) {}"

    doTest(text, resultText)
  }

  def test2(): Unit = {
    val text = s"if (a &$CARET& b) {}"
    val resultText = s"if (!(!a |$CARET| !b)) {}"

    doTest(text, resultText)
  }

  def test3(): Unit = {
    val text = s"if (!a |$CARET| b) {}"
    val resultText = s"if (!(a &$CARET& !b)) {}"

    doTest(text, resultText)
  }

  def test4(): Unit = {
    val text = s"if (a |$CARET| !b) {}"
    val resultText = s"if (!(!a &$CARET& b)) {}"

    doTest(text, resultText)
  }

  def test5(): Unit = {
    val text = s"if (!a |$CARET| !b) {}"
    val resultText = s"if (!(a &$CARET& b)) {}"

    doTest(text, resultText)
  }

  def test6(): Unit = {
    val text = s"if (!a &$CARET& b) {}"
    val resultText = s"if (!(a |$CARET| !b)) {}"

    doTest(text, resultText)
  }

  def test7(): Unit = {
    val text = s"if (a $CARET&& !b) {}"
    val resultText = s"if (!(!a $CARET|| b)) {}"

    doTest(text, resultText)
  }

  def test8(): Unit = {
    val text = s"if (!a &&$CARET !b) {}"
    val resultText = s"if (!(a ||$CARET b)) {}"

    doTest(text, resultText)
  }

  def test9(): Unit = {
    val text = s"if (true |$CARET| false) {}"
    val resultText = s"if (!(false &$CARET& true)) {}"

    doTest(text, resultText)
  }

  def test10(): Unit = {
    val text = s"!(!left &$CARET& !right)"
    val resultText = s"left |$CARET| right"

    doTest(text, resultText)
  }

  def test11(): Unit = {
    val text =
      s"""
         |val % = true
         |!(!(%) &$CARET& !(%))
      """.stripMargin
    val resultText =
      s"""
         |val % = true
         |% |$CARET| %
      """.stripMargin

    doTest(text, resultText)
  }

  def test12(): Unit = {
    val text =
      s"""
         |val % = true
         |% |$CARET| %
      """.stripMargin
    val resultText =
      s"""
         |val % = true
         |!(!(%) &$CARET& !(%))
      """.stripMargin

    doTest(text, resultText)
  }

  def test13(): Unit = {
    val text =
      s"""
         |val b = true
         |(true equals b) |$CARET| true
      """.stripMargin
    val resultText =
      s"""
         |val b = true
         |!(!(true equals b) &$CARET& false)
      """.stripMargin

    doTest(text, resultText)
  }

  def test14(): Unit = {
    val text =
      s"""
         |val b = true
         |!(!(true equals b) &$CARET& false)
      """.stripMargin
    val resultText =
      s"""
         |val b = true
         |(true equals b) |$CARET| true
      """.stripMargin

    doTest(text, resultText)
  }

  def test15(): Unit = {
    val text =
      s"""
         |val % = true
         |(%) |$CARET| (%)
      """.stripMargin
    val resultText =
      s"""
         |val % = true
         |!(!(%) &$CARET& !(%))
      """.stripMargin

    doTest(text, resultText)
  }
}