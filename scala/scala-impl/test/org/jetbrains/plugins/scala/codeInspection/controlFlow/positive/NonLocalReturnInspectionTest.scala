package org.jetbrains.plugins.scala.codeInspection.controlFlow.positive

import org.jetbrains.plugins.scala.codeInspection.controlFlow.NonLocalReturnInspectionTestBase

class NonLocalReturnInspectionTest extends NonLocalReturnInspectionTestBase {
  def test_return_from_anon_function(): Unit = checkTextHasError {
    s"""
       |  def bar: Int =
       |    Seq(1, 2, 3).map { n =>
       |      if (n > 5) ${START}return 0${END}
       |      n * 2
       |    }.sum
       |    """.stripMargin
  }

  def test_return_from_anon_partial_function(): Unit = checkTextHasError {
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
