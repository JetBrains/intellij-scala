package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

class SyntSugarMethodsTest extends ScalaLightCodeInsightFixtureTestAdapter {
  def testSCL5660(): Unit = {
    checkTextHasNoErrors(
      s"""
         |class Test {
         |  var test: Option[String] = None
         |  def test_=(test: String) { this.test = Some(test) }
         |}
         |(new Test).test = "test"
      """.stripMargin)
  }

  def testSCL5660_1(): Unit = {
    checkTextHasNoErrors(
      s"""
         |class Base {
         |  def test_=(test: String) {}
         |}
         |class Test extends Base {
         |  var test: Option[String] = None
         |}
         |(new Test).test = "test"
      """.stripMargin)
  }
}
