package org.jetbrains.plugins.scala
package codeInsight
package intention
package booleans

import com.intellij.testFramework.EditorTestUtil

/**
  * Nikolay.Tropin
  * 4/29/13
  */
class SimplifyBooleanExprWithLiteralTest extends intentions.ScalaIntentionTestBase {

  import EditorTestUtil.{CARET_TAG => CARET}

  override def familyName: String = SimplifyBooleanExprWithLiteralIntention.FamilyName

  def test_NotTrue(): Unit = {
    val text = s"$CARET!true"
    val result = s"false"
    doTest(text, result)
  }

  def test_TrueEqualsA(): Unit = {
    val text =
      s"""val a = true
         |${CARET}true == a""".stripMargin
    val result =
      s"""val a = true
         |a""".stripMargin
    doTest(text, result)
  }

  def test_TrueAndA(): Unit = {
    val text =
      s"""val a = true
         |true $CARET&& a""".stripMargin
    val result =
      s"""val a = true
         |a""".stripMargin
    doTest(text, result)
  }

  def test_AOrFalse(): Unit = {
    val text = s"val a: Boolean = false; a $CARET| false"
    val result = s"val a: Boolean = false; a"
    doTest(text, result)
  }

  def test_TwoExpressions(): Unit = {
    val text =
      s"""val a = true
         |${CARET}true && (a || false)""".stripMargin
    val result =
      s"""val a = true
         |a""".stripMargin
    doTest(text, result)
  }

  def test_TrueNotEqualsA(): Unit = {
    val text =
      s"""val a = true
         |val flag: Boolean = ${CARET}true != a""".stripMargin
    val result =
      s"""val a = true
         |val flag: Boolean = !a""".stripMargin
    doTest(text, result)
  }

  def test_SimplifyInParentheses(): Unit = {
    val text =
      s"""val a = true
         |!(${CARET}true != a)""".stripMargin
    val result =
      s"""val a = true
         |!(!a)""".stripMargin
    doTest(text, result)
  }

  def test_TrueAsAny(): Unit = {
    val text =
      s"""def trueAsAny: Any = {
         |  true
         |}
         |if (trueAsAny =$CARET= true) {
         |  println("true")
         |} else {
         |  println("false")
         |}""".stripMargin

    checkIntentionIsNotAvailable(text)
  }
}