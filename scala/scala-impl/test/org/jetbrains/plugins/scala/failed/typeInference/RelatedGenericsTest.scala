package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

class RelatedGenericsTest extends ScalaLightCodeInsightFixtureTestAdapter {

  override protected def shouldPass: Boolean = false

  def testSCL11156(): Unit = checkTextHasNoErrors(
    """
      |trait Parser[T] extends Function[String, Option[(T, String)]]
      |
      |val item: Parser[Char] = {
      |  case v => Some((v.charAt(0), v.substring(1)))
      |  case "" => None
      |}
    """.stripMargin)
}
