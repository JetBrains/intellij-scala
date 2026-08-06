package org.jetbrains.plugins.scala
package lang.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
class UninferredInPrefixTest extends ScalaLightCodeInsightFixtureTestCase {
  def testSCL18067(): Unit = checkTextHasNoErrors(
    """
      |type Receive = PartialFunction[Any, Unit]
      |def handle3: Receive = handleSpecific orElse handleSpecific orElse handleSpecific
      |def handleSpecific: Receive = ???
      |""".stripMargin
  )
}
