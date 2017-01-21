package org.jetbrains.plugins.scala
package codeInspection.functionExpressions

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.codeInspection.ScalaQuickFixTestBase

/**
  * Nikolay.Tropin
  * 9/27/13
  */
class MatchToPartialFunctionInspectionTest extends ScalaQuickFixTestBase {

  import EditorTestUtil.{SELECTION_END_TAG => END, SELECTION_START_TAG => START}

  protected val classOfInspection: Class[_ <: LocalInspectionTool] = classOf[MatchToPartialFunctionInspection]
  protected val description: String = MatchToPartialFunctionInspection.inspectionName

  def testInVal() = {
    val text =
      s"""val f: (Int) => Null = ${START}_ match $END{
         |  case 0 => null
         |  case _ => null
         |}"""
    val result =
      """val f: (Int) => Null = {
        |  case 0 => null
        |  case _ => null
        |}"""
    checkTextHasError(text)
    testQuickFix(text, result, description)
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
    testQuickFix(text, result, description)
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
    testQuickFix(text, result, description)
  }

  def testWithPossibleImplicitConversion() {
    val text =
      s"""
         |val list = List(Some(1))
         |list.map {
         |  ${START}x => x match $END{
         |    case Some(value) => value
         |    case None => 0
         |  }
         |}"""
    val result =
      """
        |val list = List(Some(1))
        |list.map {
        |  case Some(value) => value
        |  case None => 0
        |}"""
    checkTextHasError(text)
    testQuickFix(text, result, description)
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
    testQuickFix(text, result, description)
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
    testQuickFix(text, result, description)
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
