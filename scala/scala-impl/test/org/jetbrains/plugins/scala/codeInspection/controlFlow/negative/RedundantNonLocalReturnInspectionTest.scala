package org.jetbrains.plugins.scala.codeInspection.controlFlow.negative

import org.jetbrains.plugins.scala.codeInspection.controlFlow.NonLocalReturnInspectionTestBase

class RedundantNonLocalReturnInspectionTest extends NonLocalReturnInspectionTestBase {
  def test_unit_return_in_named_method_not_highlighted(): Unit = {
    disableCheckingCompilerOption()
    checkTextHasNoErrors {
      s"""
         |object Main {
         |  final def main(args: Array[String]): Unit = {
         |    val a = 1
         |    return
         |  }
         |}
         |""".stripMargin
    }
  }

  def test_return_in_if_not_highlighted(): Unit = {
    disableCheckingCompilerOption()
    checkTextHasNoErrors {
      s"""
         |  def boo: Int = {
         |    val a = Random.nextInt()
         |    if (a > 2) {
         |      return 2
         |    }
         |    return a
         |  }
         |""".stripMargin
    }
  }

  def test_return_from_anon_function_not_highlighted_with_no_compiler_option(): Unit = {
    checkTextHasNoErrors {
      s"""
         |  def bar: Int =
         |    Seq(1, 2, 3).map { n =>
         |      if (n > 5) ${START}return 0${END}
         |      n * 2
         |    }.sum
         |    """.stripMargin
    }
  }

  def test_return_from_anon_partial_function_not_highlighted_with_no_compiler_option(): Unit = {
    checkTextHasNoErrors {
      s"""
         |  def foo: Int =
         |    Seq(1, 2, 3).reduce[Int] { case (acc, x) =>
         |      val newAcc = acc + x
         |      if (newAcc > 5) ${START}return newAcc${END}
         |      newAcc
         |    }
         |    """.stripMargin
    }
  }
}
