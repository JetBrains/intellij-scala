package org.jetbrains.plugins.scala
package lang.resolve

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
class TypeVisibilityTest extends ScalaLightCodeInsightFixtureTestCase {

  def testSCL13138(): Unit = {
    val text =
      """
        |trait A[T] {
        |  type T
        |  def f(x : T) : Unit
        |}
      """.stripMargin
    checkTextHasNoErrors(text)
  }
}
