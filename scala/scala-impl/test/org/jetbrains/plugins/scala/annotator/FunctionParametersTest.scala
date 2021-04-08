package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

/**
  * @author anton.yalyshev
  * @since 14.04.16.
  */
class FunctionParametersTest extends ScalaLightCodeInsightFixtureTestAdapter {

  def testSCL12708(): Unit = {
    checkTextHasNoErrors(
      s"""
         |private def testFoo(bNum: Byte, sNum: Short, iNum: Int): Unit = { }
         |
         |testFoo(0xa, 0x2a, 0x2a)
      """.stripMargin)
  }
}
