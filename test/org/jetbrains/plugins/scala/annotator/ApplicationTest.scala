package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

/**
  * @author Nikolay.Tropin
  */
class ApplicationTest extends ScalaLightCodeInsightFixtureTestAdapter {
  def testSCL9931(): Unit = {
    checkTextHasNoErrors(
      """
        |trait Foo {
        |  def foo(a: Int) = 1
        |}
        |
        |object Foo{
        |  def foo = 0.2
        |
        |  implicit def defImpl(x: Foo.type):Foo = FooImpl
        |}
        |
        |object FooImpl extends Foo
        |
        |object Bar {
        |  Foo.foo(1) //in (1): Application does not takes parameters
        |}
      """.stripMargin)
  }

  def testSCL3878(): Unit = checkTextHasNoErrors(
    """class Test {
      |  def prop: Vector[Int] = Vector.empty[Int]  // def or val, doesn't matter
      |  def prop(x: String) = ""
      |  def test1 = List("1", "2", "3").map(prop)  // prop is red (Cannot resolve symbol prop)
      |  def test2 = List(1, 2, 3).map(prop)       // this one is ok
      |}
    """.stripMargin)

  def testScl11063(): Unit = {
    checkTextHasNoErrors(
      """
        |import scala.collection.mutable
        |
        |class DynamicMap[A](val self: mutable.Map[String, A]) extends AnyVal {
        |  def apply(key: Int): A = self(key.toString)
        |  def apply(key: Float): A = self(key.toString)
        |
        |  def update(key: String, value: A): Unit = self(key) = value
        |}
        |
        |object Example {
        |  val map = new DynamicMap(new mutable.HashMap[String, Int])
        |  <caret>map("a") = 5
        |}
      """.stripMargin)
  }

  def testDecodeRightAssoc(): Unit = {
    checkTextHasNoErrors(
      """object BacktickedRightAssoc {
        |  class Options
        |  implicit val opt: Options = new Options
        |
        |  implicit class SymbolicOperations(val arr: Array[Byte]) {
        |    def `>:`(i: Int): arr.type = ???
        |
        |    def `>::`(i: Int)(implicit opt: Options): arr.type = ???
        |  }
        |
        |  val a: Array[Byte] = ???
        |
        |  1 `>:` a
        |
        |  1 `$greater$colon` a
        |
        |  1 `>::` a
        |
        |  1 `$greater$colon$colon` a
        |}
        |""".stripMargin
    )
  }
}
