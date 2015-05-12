package org.jetbrains.plugins.scala
package codeInspection.functionExpressions

import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.codeInspection.ScalaLightInspectionFixtureTestAdapter

/**
 * Nikolay.Tropin
 * 9/27/13
 */
class MatchToPartialFunctionInspectionTest extends ScalaLightInspectionFixtureTestAdapter {
  protected val classOfInspection: Class[_ <: LocalInspectionTool] = classOf[MatchToPartialFunctionInspection]
  protected val annotation: String = MatchToPartialFunctionInspection.inspectionName

  def testInVal() = {
    val text = s"""val f: (Int) => Null = ${START}_ match $END{
                 |  case 0 => null
                 |  case _ => null
                 |}"""
    val result = """val f: (Int) => Null = {
                   |  case 0 => null
                   |  case _ => null
                   |}"""
    checkTextHasError(text)
    testFix(text, result, annotation)
  }

  def testInArgumentInParentheses() = {
    val text =
    s"""list.map(${START}x => x match $END{
      |  case Some(value) =>
      |  case None =>
      |})"""
    val result =
    """list.map {
      |  case Some(value) =>
      |  case None =>
      |}"""

    checkTextHasError(text)
    testFix(text, result, annotation)
  }

  def testInArgumentInBraces() {
    val text =
    s"""list.map {
      |  ${START}x => x match $END{
      |    case Some(value) =>
      |    case None =>
      |  }
      |}"""
    val result =
    """list.map {
      |  case Some(value) =>
      |  case None =>
      |}"""
    checkTextHasError(text)
    testFix(text, result, annotation)
  }

  def testInArgumentList() {
    val text =
    s"""def foo(f: Int => Any, i: Int)
      |foo(${START}x => x match $END{
      |  case 1 => null
      |  case _ =>
      |}, 2)"""
    val result =
    """def foo(f: Int => Any, i: Int)
      |foo({
      |  case 1 => null
      |  case _ =>
      |}, 2)"""
    checkTextHasError(text)
    testFix(text, result, annotation)
  }

  def testUseOfArgument() {
    val text =
    s"""val f: (Int) => Null = ${START}x => x match $END{
      |  case 0 =>
      |    x + 1
      |    null
      |  case _ =>
      |    x
      |    null
      |}"""
    val result =
    """val f: (Int) => Null = {
      |  case x@0 =>
      |    x + 1
      |    null
      |  case x =>
      |    x
      |    null
      |}"""
    checkTextHasError(text)
    testFix(text, result, annotation)
  }

  def testInOverloadedMethod(): Unit = {
    val text =
      s"""
         |object test {
         |  object Bar {
         |      def bar(g: Int => Unit): Unit = {
         |        g
         |      }
         |
         |      def bar(i: Int): Unit = {}
         |    }
         |
         |    Bar.bar { ${START}i => i match $END{
         |        case int_ =>
         |      }
         |    }
         |  }
         |}
         """.stripMargin
    checkTextHasNoErrors(text)
  }

  def testInOverloadedMethodInfix(): Unit = {
    val text =
     s"""
       |object test {
       |  object Bar {
       |      def bar(g: Int => Unit): Unit = {
       |        g
       |      }
       |
       |      def bar(i: Int): Unit = {}
       |    }
       |
       |    Bar bar { ${START}i => i match $END{
       |        case int_ =>
       |      }
       |    }
       |  }
       |}
     """.stripMargin
    checkTextHasNoErrors(text)
  }
}
