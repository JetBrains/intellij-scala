package org.jetbrains.plugins.scala.failed.annotator

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase

class VarAndNamedArgClashTest extends ScalaLightCodeInsightFixtureTestCase {

  override protected def shouldPass: Boolean = false

  def testSCL2194(): Unit = {
    checkHasErrorAroundCaret(
      """object t {
        |  def f(x: Int) = x
        |}
        |object test {
        |  var x = t.f(x = 2)
        |}""".stripMargin)
  }
}
