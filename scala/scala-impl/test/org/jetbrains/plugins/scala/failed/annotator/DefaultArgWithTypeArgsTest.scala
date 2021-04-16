package org.jetbrains.plugins.scala.failed.annotator

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

/**
  * @author Nikolay.Tropin
  */
class DefaultArgWithTypeArgsTest extends ScalaLightCodeInsightFixtureTestAdapter {

  override def shouldPass: Boolean = false

  def testSCL8688(): Unit = {
    checkTextHasNoErrors(
      """class Test {
        |  def foo[A, B](f: A => B = (a: A) => a) = ???
        |}
      """.stripMargin)
  }

  def testSCL13810(): Unit = {
    checkTextHasNoErrors(
      """
        |trait Obj[S]
        |implicit class Ops[S](val obj: Obj[S]) extends AnyVal {
        |  def bang[R[_]](child: String): R[S] = ???
        |}
        |trait Test[S] {
        |  def in: Obj[S]
        |  val out = in bang [Obj] "child"
        |}
      """.stripMargin)
  }
}
