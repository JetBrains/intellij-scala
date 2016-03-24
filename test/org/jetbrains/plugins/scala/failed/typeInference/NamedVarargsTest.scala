package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.junit.experimental.categories.Category

/**
  * @author Nikolay.Tropin
  */
@Category(Array(classOf[PerfCycleTests]))
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
