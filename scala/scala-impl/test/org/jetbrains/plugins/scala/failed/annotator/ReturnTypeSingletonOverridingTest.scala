package org.jetbrains.plugins.scala.failed.annotator

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

/**
  * @author Nikolay.Tropin
  */
class ReturnTypeSingletonOverridingTest extends ScalaLightCodeInsightFixtureTestAdapter {
  override protected def shouldPass: Boolean = false

  def testSCL8490(): Unit = {
    checkHasErrorAroundCaret(
      """object OverrideTest {
        |    class A {
        |      type THIS = this.type
        |
        |      def foo: this.type = this
        |      def bar: THIS = this
        |    }
        |
        |    class B extends OverrideTest.A {
        |      override def foo: B = super.foo  //incompatible type
        |
        |      override def bar: B#THIS = super.bar  //incompatible type
        |    }
        |  }
      """.stripMargin
    )
  }
}
