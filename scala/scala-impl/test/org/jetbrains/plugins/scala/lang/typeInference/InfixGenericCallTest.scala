package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

class InfixGenericCallTest extends ScalaLightCodeInsightFixtureTestAdapter {
  def testSCL17874(): Unit = checkTextHasNoErrors(
    """
      |trait ElementTrait
      |case class Element(some: Int) extends ElementTrait
      |class Rule[T] {
      |  def doStuff[T2 <: T](elem: T2 => Unit, e: T2): Unit = elem(e)
      |}
      |val rule: Rule[ElementTrait] = new Rule[ElementTrait]
      |rule doStuff[Element](a => println(a.some), Element(0))
      |""".stripMargin
  )
}
