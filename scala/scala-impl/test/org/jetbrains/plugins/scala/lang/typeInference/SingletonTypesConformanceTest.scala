package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

class SingletonTypesConformanceTest extends ScalaLightCodeInsightFixtureTestAdapter {
  def testSCL11192(): Unit = checkTextHasNoErrors(
    """
      |trait HList
      |trait Second[L <: HList] {
      |  type Out
      |  def apply(value: L): Out
      |}
      |
      |object Second {
      |  type Aux[L <: HList, O] = Second[L] {type Out = O}
      |  def apply[L <: HList](implicit inst: Second[L]): Aux[L, inst.Out] = inst
      |}
    """.stripMargin
  )
}
