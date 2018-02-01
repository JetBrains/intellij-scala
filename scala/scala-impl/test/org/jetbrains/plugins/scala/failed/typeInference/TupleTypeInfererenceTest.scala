package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.junit.experimental.categories.Category

/**
  * Created by Anton Yalyshev on 06/02/17.
  */
@Category(Array(classOf[PerfCycleTests]))
class TupleTypeInfererenceTest extends ScalaLightCodeInsightFixtureTestAdapter {

  override protected def shouldPass: Boolean = false

  def testSCL11331(): Unit =
    checkTextHasNoErrors(
      """
        |  class Extractable[T]{
        |    def unapply(arg:Any): Option[T] = None
        |  }
        |  object extractorObj extends Extractable[(Int,String)]{
        |    override def unapply(arg:Any): Option[(Int,String)] = None
        |  }
        |  val extractorVal = new Extractable[(Int,String)]
        |  null match {
        |    case extractorVal(int,string) =>
        |    case extractorObj(int,string) =>
        |  }
      """.stripMargin)

  def testSCL13064(): Unit =
    checkTextHasNoErrors(
      """
        |def apply[T](x: T) = 0
        |def update[T](y: T): T = y
        |
        |val z: ((Int, Int), Int) = this (1, 2) += 3
      """.stripMargin)
}
