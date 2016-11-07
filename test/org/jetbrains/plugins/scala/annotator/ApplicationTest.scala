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

}
