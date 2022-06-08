package org.jetbrains.plugins.scala.codeInspection.controlFlow.positive

import org.jetbrains.plugins.scala.codeInspection.controlFlow.NonLocalReturnInspectionTestBase

class RequiredNonLocalReturnInspectionTest extends NonLocalReturnInspectionTestBase {
  def test_return_from_anon_function_highlighted_without_compiler_option(): Unit = {
    disableCheckingCompilerOption()
    checkTextHasError {
      s"""
         |  def bar: Int =
         |    Seq(1, 2, 3).map { n =>
         |      if (n > 5) ${START}return 0${END}
         |      n * 2
         |    }.sum
         |    """.stripMargin
    }
  }

  def test_return_from_anon_partial_function_highlighted_without_compiler_option(): Unit = {
    disableCheckingCompilerOption()
    checkTextHasError {
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

  def test_return_from_for_statement_body_highlighted(): Unit = {
    disableCheckingCompilerOption()
    checkTextHasError {
      s"""
         |  def foo(l: List[Int]): Option[Int] = {
         |    for (x <- l) {
         |      if (x > 10)
         |        ${START}return Some(x)${END}
         |    }
         |    None
         |  }
         |    """.stripMargin
    }
  }

  def test_return_from_for_statement_generators_highlighted(): Unit = {
    disableCheckingCompilerOption()
    checkTextHasError {
      s"""
         |  def foo(): Unit = {
         |    val seq: Seq[Int] = for {
         |      x <- 1 to 10
         |      y <- 1 to 10
         |      z <- (if (y == 4) ${START}return${END} else 1 to 10)
         |    } yield {
         |      x + y + z
         |    }
         |    println(seq)
         |  }
         |    """.stripMargin
    }
  }

  def test_return_from_for_syield_highlighted(): Unit = {
    disableCheckingCompilerOption()
    checkTextHasError {
      s"""
         |  def foo(): Int = {
         |    val seq: Seq[Int] = for {
         |      x <- 1 to 10
         |      y <- 1 to 10
         |      z <- 1 to 10
         |    } yield {
         |      ${START}return x + y + z${END}
         |    }
         |    seq.sum
         |  }
         |    """.stripMargin
    }
  }
}
