package org.jetbrains.plugins.scala.failed.resolve

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.junit.experimental.categories.Category

/**
  * Created by Roman.Shein on 02.09.2016.
  */
@Category(Array(classOf[PerfCycleTests]))
class HigherKindedTypesTest extends ScalaLightCodeInsightFixtureTestAdapter {
  val START = ScalaLightCodeInsightFixtureTestAdapter.SELECTION_START
  val END = ScalaLightCodeInsightFixtureTestAdapter.SELECTION_END

  def testSCL10432(): Unit = {
    checkTextHasError(
      s"""
         |sealed abstract class CtorType[-P]
         |case class Hello[-P >: Int <: AnyVal]() extends CtorType[P] {
         |  def hello(p: P) = 123
         |}
         |
         |trait Component[-P, CT[-p] <: CtorType[p]] {
         |  val ctor: CT[P]
         |}
         |
         |implicit def toCtorOps[P >: Int <: AnyVal, CT[-p] <: CtorType[p]](base: Component[P, CT]) =
         |  base.ctor
         |
         |val example: Component[Int, Hello] = ???
         |example.ctor.hello(123)
         |val left: Int = example.hello(123)
      """.stripMargin, "Cannot resolve symbol hello")
  }
}
