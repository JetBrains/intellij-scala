package org.jetbrains.plugins.scala.failed.annotator

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.junit.experimental.categories.Category

/**
  * Created by Anton.Yalyshev on 04/12/17.
  */

@Category(Array(classOf[PerfCycleTests]))
class AsteriskTest extends ScalaLightCodeInsightFixtureTestAdapter {

  override protected def shouldPass: Boolean = false

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

  def testSCL13016(): Unit = {
    checkTextHasNoErrors(
      """
        |class Test {
        |  def f(a : (Int*) => Unit): Unit = {
        |    a.apply(1, 2)
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


}
