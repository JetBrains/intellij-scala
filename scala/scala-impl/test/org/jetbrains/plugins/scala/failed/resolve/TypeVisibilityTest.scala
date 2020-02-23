package org.jetbrains.plugins.scala.failed.resolve

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.junit.experimental.categories.Category

/**
  * @author Roman.Shein
  * @since 28.03.2016.
  */
@Category(Array(classOf[PerfCycleTests]))
class TypeVisibilityTest extends ScalaLightCodeInsightFixtureTestAdapter {

  override protected def shouldPass: Boolean = false

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
