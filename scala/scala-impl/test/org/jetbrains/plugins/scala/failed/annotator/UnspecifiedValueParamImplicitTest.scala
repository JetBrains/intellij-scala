package org.jetbrains.plugins.scala.failed.annotator

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

/**
  * Created by user on 3/28/16.
  */

class UnspecifiedValueParamImplicitTest extends ScalaLightCodeInsightFixtureTestAdapter {

  override protected def shouldPass: Boolean = false

  def testSCL5768(): Unit = {
    checkTextHasNoErrors(
      """
        |object Foo {
        |    trait Tx
        |    trait Foo { def apply()(implicit tx: Tx): Bar }
        |    trait Bar { def update(value: Int): Unit }
        |
        |    def test(foo: Foo)(implicit tx: Tx) {
        |      foo()() = 1  // second () is highlighted red
        |    }
        |  }
      """.stripMargin)
  }
}
