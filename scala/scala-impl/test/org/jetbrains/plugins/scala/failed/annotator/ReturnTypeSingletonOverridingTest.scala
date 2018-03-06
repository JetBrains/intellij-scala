package org.jetbrains.plugins.scala.failed.annotator

import org.jetbrains.plugins.scala.PerfCycleTests
import org.junit.experimental.categories.Category

/**
  * @author Nikolay.Tropin
  */
@Category(Array(classOf[PerfCycleTests]))
class ReturnTypeSingletonOverridingTest extends BadCodeGreenTestBase {
  override protected def shouldPass: Boolean = false

  def testSCL8490(): Unit = {
    doTest(
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
