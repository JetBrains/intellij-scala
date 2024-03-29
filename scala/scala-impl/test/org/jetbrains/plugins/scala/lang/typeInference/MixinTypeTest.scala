package org.jetbrains.plugins.scala
package lang.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
class MixinTypeTest extends ScalaLightCodeInsightFixtureTestCase {

  def testSCL6573(): Unit = {
    val text =
      """
        |class SCL6573 {
        |  def foo = {
        |    trait A
        |
        |    trait B
        |
        |    new A with B
        |  }
        |}
      """.stripMargin
    checkTextHasNoErrors(text)
  }
}
