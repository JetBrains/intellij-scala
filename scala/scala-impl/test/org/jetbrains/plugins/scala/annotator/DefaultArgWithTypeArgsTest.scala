package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase

class DefaultArgWithTypeArgsTest extends ScalaLightCodeInsightFixtureTestCase {

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
        |object Obj {
        |  implicit class Ops[S](val obj: Obj[S]) extends AnyVal {
        |    def bang[R[_]](child: String): R[S] = ???
        |  }
        |
        |  trait Test[S] {
        |    def in: Obj[S]
        |
        |    val out = in bang[Obj] "child"
        |  }
        |}
      """.stripMargin)
  }

  def testSCL24340(): Unit = checkTextHasNoErrors(
    """
      |object Example {
      |  def foo1[T](value: T = "42"): T = value
      |  def foo2[T](option: Option[T] = Some("42")): T = option.get
      |}
      |""".stripMargin
  )
}
