package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

class SyntheticUnapplyTupleTest extends ScalaLightCodeInsightFixtureTestAdapter {
  def testSCL16790(): Unit = checkTextHasNoErrors(
    """
      |case class SomeTestClass(param: (String, String))
      |val t = SomeTestClass("love" -> "life")
      |t match {
      |  case SomeTestClass((param1, param2)) =>
      |  case SomeTestClass(Tuple2(param1, param2)) =>
      |  case SomeTestClass(pair) =>
      |}
      |""".stripMargin
  )

  def testSCL16790Neg(): Unit = checkHasErrorAroundCaret(
    s"""
       |case class SomeTestClass(param: (String, String))
       |val t = SomeTestClass("love" -> "life")
       |t match {
       |  case SomeTestClass$CARET(t1, t2) =>
       |}
       |""".stripMargin
  )
}
