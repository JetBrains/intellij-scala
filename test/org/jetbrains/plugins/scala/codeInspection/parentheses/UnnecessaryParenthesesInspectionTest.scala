package org.jetbrains.plugins.scala
package codeInspection.parentheses

import org.jetbrains.plugins.scala.codeInspection.ScalaLightInspectionFixtureTestAdapter
import com.intellij.codeInspection.LocalInspectionTool

/**
 * Nikolay.Tropin
 * 4/29/13
 */
class UnnecessaryParenthesesInspectionTest extends ScalaLightInspectionFixtureTestAdapter{

  val annotation = "Unnecessary parentheses"
  val hintBeginning = "Remove unnecessary parentheses"
  protected def classOfInspection: Class[_ <: LocalInspectionTool] = classOf[UnnecessaryParenthesesInspection]


  def test_1(): Unit = {
    val selected = START + "(1 + 1)" + END
    check(selected)

    val text = "(<caret>1 + 1)"
    val result = "1 + 1"
    val hint = hintBeginning + " (1 + 1)"
    testFix(text, result, hint)
  }

  def test_2(): Unit = {
    val selected = "1 + " + START + "(1 * 2<caret>)" + END
    check(selected)

    val text = "1 + (1 * 2<caret>)"
    val result = "1 + 1 * 2"
    val hint = hintBeginning + " (1 * 2)"
    testFix(text, result, hint)
  }

  def test_3(): Unit = {
    val selected  = s"""
                  |def f(n: Int): Int = n match {
                  |  case even if $START(<caret>even % 2 == 0)$END => (even + 1)
                  |  case odd => 1 + (odd * 3)
                  |}
                """.stripMargin.replace("\r", "").trim
    check(selected)

    val text  = """
                  |def f(n: Int): Int = n match {
                  |  case even if (<caret>even % 2 == 0) => (even + 1)
                  |  case odd => 1 + (odd * 3)
                  |}
                """.stripMargin.replace("\r", "").trim
    val result = """
                   |def f(n: Int): Int = n match {
                   |  case even if even % 2 == 0 => (even + 1)
                   |  case odd => 1 + (odd * 3)
                   |}
                 """.stripMargin.replace("\r", "").trim
    val hint = hintBeginning + " (even % 2 == 0)"
    testFix(text, result, hint)
  }

  def test_4(): Unit = {
    val selected  = s"""
                  |def f(n: Int): Int = n match {
                  |  case even if (even % 2 == 0) => $START(even + 1<caret>)$END
                  |  case odd => 1 + (odd * 3)
                  |}
                """.stripMargin.replace("\r", "").trim
    check(selected)

    val text  = """
                  |def f(n: Int): Int = n match {
                  |  case even if (even % 2 == 0) => (even + 1<caret>)
                  |  case odd => 1 + (odd * 3)
                  |}
                """.stripMargin.replace("\r", "").trim
    val result = """
                   |def f(n: Int): Int = n match {
                   |  case even if (even % 2 == 0) => even + 1
                   |  case odd => 1 + (odd * 3)
                   |}
                 """.stripMargin.replace("\r", "").trim
    val hint = hintBeginning + " (even + 1)"
    testFix(text, result, hint)
  }

  def test_5(): Unit = {
    val selected  = s"""
                  |def f(n: Int): Int = n match {
                  |  case even if (even % 2 == 0) => (even + 1)
                  |  case odd => 1 + $START(odd * 3<caret>)$END
                  |}
                """.stripMargin.replace("\r", "").trim
    check(selected)

    val text  = """
                  |def f(n: Int): Int = n match {
                  |  case even if (even % 2 == 0) => (even + 1)
                  |  case odd => 1 + (odd * 3<caret>)
                  |}
                """.stripMargin.replace("\r", "").trim
    val result = """
                   |def f(n: Int): Int = n match {
                   |  case even if (even % 2 == 0) => (even + 1)
                   |  case odd => 1 + odd * 3
                   |}
                 """.stripMargin.replace("\r", "").trim
    val hint = hintBeginning + " (odd * 3)"
    testFix(text, result, hint)
  }

  def test_6(): Unit = {
    val selected = "val a = " + START + "((<caret>(1)))" + END
    check(selected)

    val text = "val a = ((<caret>(1)))"
    val result = "val a = 1"
    val hint = hintBeginning + " (((1)))"
    testFix(text, result, hint)
  }

  def test_7(): Unit = {
    val text  = """def a(x: Any): Boolean = true
                      |List() count (a(_))""".stripMargin.replace("\r", "").trim
    checkTextHasNoErrors(text, annotation, classOf[UnnecessaryParenthesesInspection])
  }

  def test_8(): Unit = {
    val selected = "1 to " + START +"((1, 2))" + END
    check(selected)

    val text = "1 to ((1, 2))"
    val result = "1 to (1, 2)"
    val hint = hintBeginning + " ((1, 2))"
    testFix(text, result, hint)
  }

  def test_9(): Unit = {
    val text = """(List("a")
                 |    :+ new String("b")
                 |    :+ new String("c")
                 |    :+ new String("d"))"""
    checkHasNoErrors(text)
  }

}
