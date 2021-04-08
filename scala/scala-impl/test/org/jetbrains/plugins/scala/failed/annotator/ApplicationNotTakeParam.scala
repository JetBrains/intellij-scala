package org.jetbrains.plugins.scala.failed.annotator

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

/**
  * Created by kate on 3/29/16.
  */
class ApplicationNotTakeParam_Failing extends ScalaLightCodeInsightFixtureTestAdapter {

  override protected def shouldPass: Boolean = false

  def testSCL10902(): Unit = {
    checkTextHasNoErrors(
      """
        |object Test extends App {
        | class A { def apply[Z] = 42 }
        | def create = new A
        |
        | create[String]
        |}
      """.stripMargin)
  }

  def testSCL12447(): Unit = {
    checkTextHasNoErrors(
      """
        |1.##()
      """.stripMargin)
  }

  def testSCL13689(): Unit = {
    checkTextHasNoErrors(
      """
        |class parent {
        |  def abc[T]: T = ???
        |}
        |
        |object foo extends parent {
        |  def abc: Nothing = ???
        |}
        |
        |object bar {
        |  foo.abc[Int]
        |}
      """.stripMargin)
  }
}
