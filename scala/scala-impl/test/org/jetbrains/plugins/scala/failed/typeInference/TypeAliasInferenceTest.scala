package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.junit.experimental.categories.Category

/**
  * @author Anton Yalyshev
  * @since 10.01.2018.
  */
@Category(Array(classOf[PerfCycleTests]))
class TypeAliasInferenceTest extends ScalaLightCodeInsightFixtureTestAdapter {

  def testSCL13137(): Unit = {
    checkTextHasNoErrors(
      """
        |trait A[T] {
        |  def f(x : T => T) : Unit = { }
        |  def g(a: A[_]) : Unit = a.f(z => z)
        |}
      """.stripMargin)
  }

  def testSCL13139(): Unit = {
    checkTextHasNoErrors(
      """
        |trait A {
        |  type T
        |  def f(a: A)(x : a.T => a.T)
        |  def g(a: A) : Unit = a.f(a)((z : a.T) => z)
        |}
      """.stripMargin)
  }
}
