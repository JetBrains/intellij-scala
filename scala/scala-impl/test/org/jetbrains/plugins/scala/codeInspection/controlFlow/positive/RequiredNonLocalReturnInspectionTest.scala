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

  def test_return_from_for_yield_highlighted(): Unit = {
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

  def test_multiple_returns_highlighted(): Unit = {
    disableCheckingCompilerOption()
    checkTextHasError {
      s"""
         |  def foo(x: Int): Int = {
         |    val seq = 1 to 3
         |    seq.foreach { _ =>
         |      if (x == 1) {
         |        ${START}return 1${END}
         |      }
         |    }
         |    seq.foreach { _ =>
         |      if (x == 2) {
         |        ${START}return 2${END}
         |      }
         |    }
         |    seq.foreach { _ =>
         |      if (x == 3) {
         |        ${START}return 3${END}
         |      }
         |    }
         |
         |    0
         |  }
         |    """.stripMargin
    }
  }

  def test_nonlocal_return_highlight_when_in_anon_functions_in_synchronized(): Unit = {
    disableCheckingCompilerOption()
    checkTextHasError {
      s"""
         |  private val lock = new AnyRef
         |
         |  def foo(x: Int): Int = synchronized {
         |    lock.synchronized {
         |      Seq(1, 2, 3).map { n =>
         |        if (n > 5) ${START}return 0${END}
         |        n * 2
         |      }.sum
         |      return 2
         |    }
         |    lock.synchronized {
         |      Seq(1, 2, 3).map { n =>
         |        if (n > 5) ${START}return 0${END}
         |        n * 2
         |      }.sum
         |      return 2
         |    }
         |    1
         |  }
         |    """.stripMargin
    }
  }
}
