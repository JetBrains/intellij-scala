package org.jetbrains.plugins.scala.lang.resolve

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

/**
  * @author Roman.Shein
  * @since 28.03.2016.
  */
class TypeVisibilityTest extends ScalaLightCodeInsightFixtureTestAdapter {

  def testSCL13138(): Unit = {
    val text =
      """
        |trait A[T] {
        |  type T
        |  def f(x : T) : Unit
        |}
      """.stripMargin
    checkTextHasNoErrors(text)
  }
}
