package org.jetbrains.plugins.scala
package codeInsight
package intention
package booleans

import com.intellij.testFramework.EditorTestUtil

class DeMorganLawIntentionTest extends intentions.ScalaIntentionTestBase {

  import EditorTestUtil.{CARET_TAG => CARET}

  override def familyName = ScalaCodeInsightBundle.message("family.name.demorgan.law")

  def doTestBothWays(text: String, resultText: String): Unit = {
    doTest(text, resultText)
    doTest(resultText, text)
  }

  def test1(): Unit = {
    val text = s"if (a |$CARET| b) {}"
    val resultText = s"if (!(!a &$CARET& !b)) {}"

    doTestBothWays(text, resultText)
  }

  def test2(): Unit = {
    val text = s"if (a &$CARET& b) {}"
    val resultText = s"if (!(!a |$CARET| !b)) {}"

    doTestBothWays(text, resultText)
  }

  def test3(): Unit = {
    val text = s"if (!a |$CARET| b) {}"
    val resultText = s"if (!(a &$CARET& !b)) {}"

    doTestBothWays(text, resultText)
  }

  def test4(): Unit = {
    val text = s"if (a |$CARET| !b) {}"
    val resultText = s"if (!(!a &$CARET& b)) {}"

    doTestBothWays(text, resultText)
  }

  def test5(): Unit = {
    val text = s"if (!a |$CARET| !b) {}"
    val resultText = s"if (!(a &$CARET& b)) {}"

    doTestBothWays(text, resultText)
  }

  def test6(): Unit = {
    val text = s"if (!a &$CARET& b) {}"
    val resultText = s"if (!(a |$CARET| !b)) {}"

    doTestBothWays(text, resultText)
  }

  def test7(): Unit = {
    val text = s"if (a $CARET&& !b) {}"
    val resultText = s"if (!(!a $CARET|| b)) {}"

    doTestBothWays(text, resultText)
  }

  def test8(): Unit = {
    val text = s"if (!a &&$CARET !b) {}"
    val resultText = s"if (!(a ||$CARET b)) {}"

    doTestBothWays(text, resultText)
  }

  def test9(): Unit = {
    val text = s"if (true |$CARET| false) {}"
    val resultText = s"if (!(false &$CARET& true)) {}"

    doTestBothWays(text, resultText)
  }

  def test10(): Unit = {
    val text = s"!(!left &$CARET& !right)"
    val resultText = s"left |$CARET| right"

    doTestBothWays(text, resultText)
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

    doTestBothWays(text, resultText)
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

    doTestBothWays(text, resultText)
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

    doTestBothWays(text, resultText)
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

    doTestBothWays(text, resultText)
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

  def testOrChain(): Unit = {
    val text =
      s"""
         |!a |$CARET| !b || !c
      """.stripMargin
    val resultText =
      s"""
         |!(a &$CARET& b && c)
      """.stripMargin

    doTestBothWays(text, resultText)
  }

  def testOrChain2(): Unit = {
    val text =
      s"""
         |!a || !b |$CARET| !c
      """.stripMargin
    val resultText =
      s"""
         |!(a && b &$CARET& c)
      """.stripMargin

    doTestBothWays(text, resultText)
  }

  def testOrChain3(): Unit = {
    val text =
      s"""
         |!a || !b |$CARET| !c || !d
      """.stripMargin
    val resultText =
      s"""
         |!(a && b &$CARET& c && d)
      """.stripMargin

    doTestBothWays(text, resultText)
  }

  def testAndChain1(): Unit = {
    val text =
      s"""
         |!a || b |$CARET| c || !d
      """.stripMargin
    val resultText =
      s"""
         |!(a && !b &$CARET& !c && d)
      """.stripMargin

    doTestBothWays(text, resultText)
  }

  def testMixedChain1(): Unit = {
    val text =
      s"""
         |!a || !b &$CARET& !c || !d
      """.stripMargin
    val resultText =
      s"""
         |!a || !(b |$CARET| c) || !d
      """.stripMargin

    doTestBothWays(text, resultText)
  }

  def testMixedChain2(): Unit = {
    val text =
      s"""
         |!a && !b |$CARET| !c && !d
      """.stripMargin
    val resultText =
      s"""
         |!(!(!a && !b) &$CARET& !(!c && !d))
      """.stripMargin

    doTest(text, resultText)
  }

  def testMixedChain3(): Unit = {
    val text =
      s"""
         |!a || !b |$CARET| !(c && d)
      """.stripMargin
    val resultText =
      s"""
         |!(a && b &$CARET& c && d)
      """.stripMargin

    doTest(text, resultText)
  }


  def testMixedChain4(): Unit = {
    val text =
      s"""
         |a op a || b + b |$CARET| c * c
      """.stripMargin
    val resultText =
      s"""
         |a op !(!a && !(b + b) &$CARET& !(c * c))
      """.stripMargin

    doTestBothWays(text, resultText)
  }
}