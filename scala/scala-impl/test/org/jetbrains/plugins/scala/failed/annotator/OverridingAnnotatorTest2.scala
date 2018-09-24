package org.jetbrains.plugins.scala.failed.annotator

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.junit.experimental.categories.Category

/**
  * @author Roman.Shein
  * @since 28.03.2016.
  */
@Category(Array(classOf[PerfCycleTests]))
class OverridingAnnotatorTest2 extends ScalaLightCodeInsightFixtureTestAdapter {

  override protected def shouldPass: Boolean = false

  //TODO: the issue does not reproduce when test is performed  using OverridingAnnotatorTest
  def testSCL3807(): Unit = {
    checkTextHasNoErrors(
      """
        |trait A {
        |  def foo(f: (=> A) => Int) = {_: A => 42}
        |}
        |
        |object A extends A{
        |  def foo(f: (A) => Int) = null
        |}
      """.stripMargin)
  }

  def testScl2071(): Unit = {
    checkTextHasNoErrors(
      """
        |  def doSmth(p: String) {}
        |  def doSmth(p: => String) {}
      """.stripMargin)
  }

  def testScl9034(): Unit = {
    checkTextHasNoErrors(
      """
        |  def apply(list: Iterable[Int]): ErrorHighlighting = { this ("int") }
        |  def apply(list: => Iterable[String]): ErrorHighlighting = { this ("string") }
      """.stripMargin)
  }

  def testScl12605(): Unit = {
    checkTextHasNoErrors(
      """
        |class Bug {
        |  def main(args: Array[String]): Unit = {
        |    val bug = new Bug()
        |    bug.buggy(bug, (x, y) => x + y)
        |  }
        |
        |  def buggy(y: Bug): Bug = ???
        |
        |  def buggy(y: Bug, function: DDFunction): Bug = ???
        |}
        |
        |trait DDFunction {
        |  def apply(x: Double, y: Double): Double
        |}
      """.stripMargin)
  }

  def testScl13039(): Unit = {
    checkTextHasNoErrors(
      """
        |trait Test[T] {
        |  def foo[S](x : T) : Unit = {
        |    val t = new Test[S] {
        |      override def foo[U](x: S): Unit = { }
        |    }
        |  }
        |}
      """.stripMargin)
  }

  def testScl13027(): Unit = {
    checkTextHasNoErrors(
      """
        |object Test {
        |  class returnType[T]
        |
        |  object myObject {
        |    implicit object intType
        |    def myFunction(fun: Int => Unit)(implicit i: intType.type): returnType[Int] = new returnType[Int]
        |
        |    implicit object strType
        |    def myFunction(fun: String => Unit)(implicit i: strType.type): returnType[String] = new returnType[String]
        |  }
        |
        |
        |  (myObject myFunction (_ + 1)): returnType[Int] // compiles, but red "Cannot resolve reference myFunction with such signature"
        |  (myObject myFunction (_.toUpperCase + 1)): returnType[String] // compiles, but red "Cannot resolve reference myFunction with such signature"
        |}
      """.stripMargin)
  }

  def testScl13920(): Unit = {
    checkTextHasNoErrors(
      """
        |trait TBase {
        |  trait TProperty
        |  type Property <: TProperty
        |}
        |
        |trait TSub1 extends TBase {
        |  trait TProperty extends super.TProperty {
        |    def sub1(): String
        |  }
        |  override type Property <: TProperty
        |}
        |
        |trait TSub2 extends TBase {
        |  trait TProperty extends super.TProperty {
        |    def sub2(): String
        |  }
        |  override type Property <: TProperty
        |}
        |
        |trait TSub1AndSub2 extends TSub1 with TSub2 {
        |  trait TProperty extends super[TSub1].TProperty with super[TSub2].TProperty
        |  override type Property <: TProperty
        |}
        |
        |class Sub1AndSub2 extends TSub1AndSub2 {
        |  override type Property = TProperty
        |
        |  case class PropImpl() extends Property {
        |    override def sub1(): String = "sub1"
        |    override def sub2(): String = "sub2"
        |  }
        |
        |  object Property {
        |    def apply(): Property = PropImpl()
        |  }
        |}
      """.stripMargin)
  }
}
