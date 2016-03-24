package org.jetbrains.plugins.scala.failed.annotator

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.junit.experimental.categories.Category

/**
  * User: Dmitry.Naydanov
  * Date: 24.03.16.
  */
@Category(Array(classOf[PerfCycleTests]))
class CantCreateTempDefTest extends ScalaLightCodeInsightFixtureTestAdapter {
  def testSCL7062() = {
    checkTextHasNoErrors(
      """  object Test {
        |
        |  trait Base[T] {
        |    def insert(value: T): Unit
        |  }
        |
        |  abstract class Bar[T] extends Base[T] {
        |    override def insert(values: T): Unit = Unit
        |
        |    def foo1[R](right: Base[R]): Base[R] = new Bar[R]() {}
        |
        |    def foo2[R](right: Base[R]): Base[(T, R)] = createBar
        |
        |    def foo3[R](right: Base[R]): Base[(T, R)] = new Bar[(T, R)]() {}   // constructor call is underlined red
        |  }
        |
        |  def createBar[T, R] = new Bar[(T, R)]() {}
        |}""".stripMargin
    )
  }
}
