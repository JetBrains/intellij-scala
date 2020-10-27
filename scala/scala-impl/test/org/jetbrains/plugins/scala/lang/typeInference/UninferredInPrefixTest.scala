package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

class UninferredInPrefixTest extends ScalaLightCodeInsightFixtureTestAdapter {
  def testSCL18067(): Unit = checkTextHasNoErrors(
    """
      |type Receive = PartialFunction[Any, Unit]
      |def handle3: Receive = handleSpecific orElse handleSpecific orElse handleSpecific
      |def handleSpecific: Receive = ???
      |""".stripMargin
  )
}
