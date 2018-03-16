package org.jetbrains.plugins.scala
package codeInspection
package parentheses

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

/**
  * Nikolay.Tropin
  * 4/29/13
  */
class UnnecessaryParenthesesInspectionTest extends ScalaQuickFixTestBase {

  import CodeInsightTestFixture.CARET_MARKER
  import EditorTestUtil.{SELECTION_END_TAG => END, SELECTION_START_TAG => START}

  protected override val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[ScalaUnnecessaryParenthesesInspection]

  protected override val description = "Unnecessary parentheses"

  private val hintBeginning = "Remove unnecessary parentheses"

  def test_1(): Unit = {
    val selected = s"$START(1 + 1)$END"
    checkTextHasError(selected)

    val text = s"(${CARET_MARKER}1 + 1)"
    val result = "1 + 1"
    val hint = hintBeginning + " (1 + 1)"
    testQuickFix(text, result, hint)
  }

  def test_2(): Unit = {
    val text = "1 + (1 * 2)"
    checkTextHasNoErrors(text)
  }

  def test_3(): Unit = {
    val selected =
      s"""
         |def f(n: Int): Int = n match {
         |  case even if $START(even % 2 == 0)$END => (even + 1)
         |  case odd => 1 + (odd * 3)
         |}
                """
    checkTextHasError(selected)

    val text =
      s"""
         |def f(n: Int): Int = n match {
         |  case even if (${CARET_MARKER}even % 2 == 0) => (even + 1)
         |  case odd => 1 + (odd * 3)
         |}
      """
    val result =
      """
        |def f(n: Int): Int = n match {
        |  case even if even % 2 == 0 => (even + 1)
        |  case odd => 1 + (odd * 3)
        |}
      """
    val hint = hintBeginning + " (even % 2 == 0)"
    testQuickFix(text, result, hint)
  }

  def test_4(): Unit = {
    val selected =
      s"""
         |def f(n: Int): Int = n match {
         |  case even if (even % 2 == 0) => $START(even + 1)$END
         |  case odd => 1 + (odd * 3)
         |}
                """
    checkTextHasError(selected)

    val text =
      s"""
         |def f(n: Int): Int = n match {
         |  case even if (even % 2 == 0) => (even + 1$CARET_MARKER)
         |  case odd => 1 + (odd * 3)
         |}
      """
    val result =
      """
        |def f(n: Int): Int = n match {
        |  case even if (even % 2 == 0) => even + 1
        |  case odd => 1 + (odd * 3)
        |}
      """
    val hint = hintBeginning + " (even + 1)"
    testQuickFix(text, result, hint)
  }

  def test_5(): Unit = {
    val text = "1 :: (2 :: Nil)"
    checkTextHasNoErrors(text)
  }

  def test_6(): Unit = {
    val selected = s"val a = $START(((1)))$END"
    checkTextHasError(selected)

    val text = s"val a = (($CARET_MARKER(1)))"
    val result = "val a = 1"
    val hint = hintBeginning + " (((1)))"
    testQuickFix(text, result, hint)
  }

  def test_7(): Unit = {
    val text =
      """def a(x: Any): Boolean = true
        |List() count (a(_))"""
    checkTextHasNoErrors(text)
  }

  def test_8(): Unit = {
    val selected = s"1 to $START((1, 2))$END"
    checkTextHasError(selected)

    val text = "1 to ((1, 2))"
    val result = "1 to (1, 2)"
    val hint = hintBeginning + " ((1, 2))"
    testQuickFix(text, result, hint)
  }

  def test_9(): Unit = {
    val text =
      """(List("a")
        |    :+ new String("b")
        |    :+ new String("c")
        |    :+ new String("d"))"""
    checkTextHasNoErrors(text)
  }

  def test_10(): Unit = {
    val selected = s"$START(/*b*/ 1 + /*a*/ 1 /*comment*/)$END"
    checkTextHasError(selected)

    val text = s"($CARET_MARKER/*b*/ 1 + /*a*/ 1 /*comment*/)"
    val result = "/*b*/ 1 + /*a*/ 1 /*comment*/"
    val hint = hintBeginning + " (1 + 1)"
    testQuickFix(text, result, hint)
  }

  def test_11(): Unit = {
    val selected = s"$START(/*1*/ 6 /*2*/ /*3*/)$END"
    checkTextHasError(selected)

    val text = s"($CARET_MARKER/*1*/ 6 /*2*/ /*3*/)"
    val result = "/*1*/ 6 /*2*/\n\r/*3*/"
    val hint = hintBeginning + " (6)"
    testQuickFix(text, result, hint)
  }


  def test_simpleType(): Unit = {
    val selected = s"val i: $START(Int)$END = 3"
    checkTextHasError(selected)

    val text = s"val i: ($CARET_MARKER Int) = 3"
    val result = "val i: Int = 3"
    val hint = hintBeginning + " (Int)"
    testQuickFix(text, result, hint)
  }


  def test_simpleTypeMultipleParen(): Unit = {
    val selected = s"val i: $START(((Int)))$END = 3"
    checkTextHasError(selected)

    val text = "val i: (((Int))) = 3"
    val result = "val i: Int = 3"
    val hint = hintBeginning + " (((Int)))"
    testQuickFix(text, result, hint)
  }

  def test_functionType():Unit={
    val selected = s"val i: Int => $START(Int => String)$END = _"
    checkTextHasError(selected)

    val text = "val i: Int => (Int => String) = _"
    val result = "val i: Int => Int => String = _"
    val hint = hintBeginning + " (Int => String)"
    testQuickFix(text, result, hint)
  }

  def test_functionPlusInfix(): Unit = {
    // these are clarifying
    val selected = s"val i: Int => $START(A op B)$END = _"
    checkTextHasNoErrors(selected)
  }

  def test_infixType_rightAssoc():Unit = {
    val selected = s"val f: Int <<: $START(Unit <<: Unit)$END = _"
    checkTextHasError(selected)

    val text = s"val f: Int <<: ($CARET_MARKER Unit <<: Unit) = _"
    val result = "val f: Int <<: Unit <<: Unit = _"
    val hint = hintBeginning + " (Unit <<: Unit)"
    testQuickFix(text, result, hint)
  }

 
  def test_infixType_leftAssoc():Unit = {
    val selected = s"val f: $START(Int op Unit)$END op Unit = _"
    checkTextHasError(selected)

    val text = s"val f: ($CARET_MARKER Int op Unit) op Unit = _"
    val result = "val f: Int op Unit op Unit = _"
    val hint = hintBeginning + " (Int op Unit)"
    testQuickFix(text, result, hint)
  }


  def test_tupleType(): Unit = {
    val selected = s"val f: $START((Int, String))$END = _"
    checkTextHasError(selected)

    val text = s"val f: ($CARET_MARKER(Int, Unit)) = _"
    val result = "val f: (Int, Unit) = _"
    val hint = hintBeginning + " ((Int, Unit))"
    testQuickFix(text, result, hint)
  }


  def test_infxPatternPrecedence(): Unit = {
    val selected = s"val a +: $START(b +: c)$END = _ "
    checkTextHasError(selected)

    val text = s"val a +: ($CARET_MARKER b +: c) = _ "
    val result = "val a +: b +: c = _ "
    val hint = hintBeginning + " (b +: c)"
    testQuickFix(text, result, hint)
  }


  def test_lambdaParam(): Unit = {
    val r1 = s"Seq(1) map { $START(i)$END => i + 1 }"
    val r2 = s"Seq(1) map { $START(i: Int)$END => i + 1 }"
    val r3 = s"Seq(1) map ($START(i)$END => i + 1)"
    val required = "Seq(1) map ((i: Int) => i + 1)"

    checkTextHasNoErrors(required)
    checkTextHasError(r1)
    checkTextHasError(r2)
    checkTextHasError(r3)

    val text = s"Seq(1) map { (${CARET_MARKER}i: Int) => i + 1 }"
    val result = s"Seq(1) map { i: Int => i + 1 }"
    val hint = hintBeginning + " (i: Int)"
    testQuickFix(text, result, hint)
  }
}
