package org.jetbrains.plugins.scala
package annotator

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
class FunctionParametersTest extends ScalaLightCodeInsightFixtureTestCase {

  def testSCL12708(): Unit = {
    checkTextHasNoErrors(
      s"""
         |private def testFoo(bNum: Byte, sNum: Short, iNum: Int): Unit = { }
         |
         |testFoo(0xa, 0x2a, 0x2a)
      """.stripMargin)
  }
}
