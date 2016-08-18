package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.junit.experimental.categories.Category

/**
  * Created by Anton Yalyshev on 14/07/16.
  */

@Category(Array(classOf[PerfCycleTests]))
class BoundsConformanceTest extends ScalaLightCodeInsightFixtureTestAdapter {

  def testSCL10029(): Unit = {
    checkTextHasNoErrors(
      """
        |sealed trait Feeling
        |sealed trait Hungry extends Feeling
        |sealed trait Thirsty extends Feeling
        |
        |class Person[F <: Feeling] {
        |  def eat[T >: F <: Hungry] = println("Chomp!")
        |  def drink[T >: F <: Thirsty] = println("Glug!")
        |}
      """.stripMargin
    )
  }

  def testSCL10692(): Unit = {
    checkTextHasNoErrors(
      """
        |trait A {
        |  type B[+T]
        |  type C[+T] <: B[T]
        |  def c: C[Int]
        |}
        |
        |object Q {
        |  val a: A = ???
        |  val b: a.B[Int] = a.c
        |}
      """.stripMargin
    )
  }
}
