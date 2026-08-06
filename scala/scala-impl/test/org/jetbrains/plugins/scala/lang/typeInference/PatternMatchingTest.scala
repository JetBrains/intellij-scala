package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase

class PatternMatchingTest extends ScalaLightCodeInsightFixtureTestCase {
  def testSCL13151(): Unit = {
    val text =
      """
        |class Term[A]
        |class Number(val n: Int) extends Term[Int]
        |object X {
        |  def f[B](t: Term[B]): B = t match {
        |    case y: Number => y.n // False error: Expression of type Int doesn't conform to expected type B
        |  }
        |}
      """.stripMargin
    checkTextHasNoErrors(text)
  }

  def testSCL13141(): Unit = {
    val text =
      """
        |object X {
        |  def f(x : List[_]): Any = x match { case z : List[a] => { val e : a = z.head; e } }
        |}
      """.stripMargin
    checkTextHasNoErrors(text)
  }
}