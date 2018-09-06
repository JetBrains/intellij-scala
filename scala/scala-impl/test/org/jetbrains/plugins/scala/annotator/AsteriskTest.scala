package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

/**
  * Created by Anton.Yalyshev on 04/12/17.
  */

class AsteriskTest extends ScalaLightCodeInsightFixtureTestAdapter {

  def testSCL13015(): Unit = {
    checkTextHasNoErrors(
      """
        |class Test {
        |  def f(a : Int*): Unit = {
        |    val g : (Int*) => Unit = f // False error: Expression of type Seq[Int] => Unit doesn't conform to expected type Int => Unit
        |  }
        |}
      """.stripMargin)
  }

  def testSCL13017(): Unit = {
    checkTextHasNoErrors(
      """
        |class Test {
        |  def f(): Unit = {
        |    val a : (Int*) => Unit = x => { val y: Seq[Int] = x }
        |  }
        |}
      """.stripMargin)
  }

  def testSCL13018(): Unit = {
    checkTextHasNoErrors(
      """
        |class Test {
        |  def f(a: (Int) => Unit *): Unit = {
        |    f(x => {}, y => {})
        |  }
        |}
      """.stripMargin)
  }
}

