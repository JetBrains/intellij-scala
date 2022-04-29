package org.jetbrains.plugins.scala.codeInspection.controlFlow.negative

import org.jetbrains.plugins.scala.codeInspection.controlFlow.NonLocalReturnInspectionTestBase

class RedundantNonLocalReturnInspectionTest extends NonLocalReturnInspectionTestBase {
  def test_unit_return_in_named_method(): Unit = checkTextHasNoErrors {
    s"""
       |object Main {
       |  final def main(args: Array[String]): Unit = {
       |    val a = 1
       |    return
       |  }
       |}
       |""".stripMargin
  }

  def test_return_in_if(): Unit = checkTextHasNoErrors {
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
