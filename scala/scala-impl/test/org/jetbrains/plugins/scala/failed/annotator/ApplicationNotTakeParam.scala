package org.jetbrains.plugins.scala.failed.annotator

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase

class ApplicationNotTakeParam_Failing extends ScalaLightCodeInsightFixtureTestCase {

  override protected def shouldPass: Boolean = false

  def testSCL12447(): Unit = {
    checkTextHasNoErrors(
      """
        |1.##()
      """.stripMargin)
  }
}
