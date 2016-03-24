package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

/**
  * @author Nikolay.Tropin
  */
class NamedVarargsTest extends ScalaLightCodeInsightFixtureTestAdapter {

  def testSCL7471(): Unit = {
    val text =
      """object Test {
        |  def test(xs: Int*) {}
        |
        |  test(xs =  Seq(1, 2):  _*)
        |}
      """.stripMargin
    checkTextHasNoErrors(text)
  }
}
