package org.jetbrains.plugins.scala
package codeInspection
package functionExpressions

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.EditorTestUtil

/**
  * Nikolay.Tropin
  * 9/27/13
  */
class MatchToPartialFunctionInspectionTest extends ScalaQuickFixTestBase {

  import EditorTestUtil.{SELECTION_END_TAG => END, SELECTION_START_TAG => START}
  import MatchToPartialFunctionInspection.DESCRIPTION

  protected val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[MatchToPartialFunctionInspection]

  protected val description: String = DESCRIPTION

  def testInVal(): Unit = testQuickFix(
    text =
      s"""val f: (Int) => Null = ${START}_ match $END{
         |  case 0 => null
         |  case _ => null
         |}
         """.stripMargin,
    expected =
      """val f: (Int) => Null = {
        |  case 0 => null
        |  case _ => null
        |}
      """.stripMargin
  )

  def testInArgumentInParentheses(): Unit = testQuickFix(
    text =
      s"""list.map(${START}x => x match $END{
         |  case Some(value) =>
         |  case None =>
         |})
         """.stripMargin,
    expected =
      """list.map {
        |  case Some(value) =>
        |  case None =>
        |}
      """.stripMargin
  )

  def testInArgumentInBraces(): Unit = testQuickFix(
    text =
      s"""list.map {
         |  ${START}x => x match $END{
         |    case Some(value) =>
         |    case None =>
         |  }
         |}
         """.stripMargin,
    expected =
      """list.map {
        |  case Some(value) =>
        |  case None =>
        |}
      """.stripMargin
  )

  def testWithPossibleImplicitConversion(): Unit = testQuickFix(
    text =
      s"""
         |val list = List(Some(1))
         |list.map {
         |  ${START}x => x match $END{
         |    case Some(value) => value
         |    case None => 0
         |  }
         |}
        """.stripMargin,
    expected =
      """
        |val list = List(Some(1))
        |list.map {
        |  case Some(value) => value
        |  case None => 0
        |}
      """.stripMargin
  )

  def testInArgumentList(): Unit = testQuickFix(
    text =
      s"""def foo(f: Int => Any, i: Int)
         |foo(${START}x => x match $END{
         |  case 1 => null
         |  case _ =>
         |}, 2)
         """.stripMargin,
    expected =
      """def foo(f: Int => Any, i: Int)
        |foo({
        |  case 1 => null
        |  case _ =>
        |}, 2)
      """.stripMargin
  )

  def testUseOfArgument(): Unit = testQuickFix(
    text =
      s"""val f: (Int) => Null = ${START}x => x match $END{
         |  case 0 =>
         |    x + 1
         |    null
         |  case _ =>
         |    x
         |    null
         |}
        """.stripMargin,
    expected =
      """val f: (Int) => Null = {
        |  case x@0 =>
        |    x + 1
        |    null
        |  case x =>
        |    x
        |    null
        |}
      """.stripMargin
  )

  def testInOverloadedMethod(): Unit = checkTextHasNoErrors(
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
  )

  def testInOverloadedMethodInfix(): Unit = checkTextHasNoErrors(
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
  )

  private def testQuickFix(text: String, expected: String): Unit = {
    testQuickFix(text, expected, description)
  }
}
