package org.jetbrains.plugins.scala
package lang.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
class Refinement extends ScalaLightCodeInsightFixtureTestCase {
  def testSCL20698(): Unit = {
    val text =
      """
        |object Example {
        |  val v: AnyRef = ??? : { def foo: Int }
        |}
      """.stripMargin
    checkTextHasNoErrors(text)
  }
}
