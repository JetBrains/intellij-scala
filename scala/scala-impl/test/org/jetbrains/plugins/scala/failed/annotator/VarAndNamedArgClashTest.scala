package org.jetbrains.plugins.scala.failed.annotator

import org.jetbrains.plugins.scala.PerfCycleTests
import org.junit.experimental.categories.Category

/**
  * @author Nikolay.Tropin
  */
@Category(Array(classOf[PerfCycleTests]))
class VarAndNamedArgClashTest extends BadCodeGreenTestBase {

  override protected def shouldPass: Boolean = false

  def testSCL2194(): Unit = {
    doTest(
      """object t {
        |  def f(x: Int) = x
        |}
        |object test {
        |  var x = t.f(x = 2)
        |}""".stripMargin)
  }
}
