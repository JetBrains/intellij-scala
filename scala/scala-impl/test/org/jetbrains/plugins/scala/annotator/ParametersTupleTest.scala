package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

/**
  * @author Roman.Shein
  *         Date: 13.12.2016
  */
class ParametersTupleTest extends AnnotatorLightCodeInsightFixtureTestAdapter {
  def testSCL11092(): Unit = {
    checkTextHasNoErrors(
      """
        |class SCL11092 {
        |  def fTuple[A, B](heading : (String, String), rows : (A, B)): Unit = 42
        |
        |  fTuple(("a", "b"), (1, 2))
        |}
      """.stripMargin)
  }
}
