package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase

class TupleTypeInfererenceTest extends ScalaLightCodeInsightFixtureTestCase {

  override protected def shouldPass: Boolean = false

  def testSCL13064(): Unit =
    checkTextHasNoErrors(
      """
        |object Test {
        |  def apply[T](x: T) = 0
        |  def update[T](y: T): T = y
        |
        |  def foo(): Unit = {
        |    val z: ((Int, Int), Int) = this(1, 2) += 3
        |  }
        |}
      """.stripMargin)
}
