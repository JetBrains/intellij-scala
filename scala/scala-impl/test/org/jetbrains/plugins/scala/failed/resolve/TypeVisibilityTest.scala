package org.jetbrains.plugins.scala.failed.resolve

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.junit.experimental.categories.Category

/**
  * @author Roman.Shein
  * @since 28.03.2016.
  */
@Category(Array(classOf[PerfCycleTests]))
class TypeVisibilityTest extends ScalaLightCodeInsightFixtureTestAdapter {

  override protected def shouldPass: Boolean = false

  def testSCL9178() = {
    val text =
      """
        |trait Example {
        |  protected type EXAMPLE <: Example
        |  def example() : EXAMPLE
        |}
        |
        |trait ExtendedExample extends Example {
        |  final protected type EXAMPLE = ExtendedExample
        |  def myDef() : Unit
        |}
        |
        |trait Something extends Example {
        |  val exampleVal = example()
        |  def falseErrorHere() = {
        |    val extendedSomethingInstance = new ExtendedSomethingClass
        |    extendedSomethingInstance.exampleVal.myDef  //<-- Cannot resolve symbol myDef
        |  }
        |}
        |
        |trait ExtendedSomething extends Something with ExtendedExample {
        |}
        |
        |class ExtendedSomethingClass extends ExtendedSomething {
        |  def myDef() : Unit = {}
        |  def example() = new ExtendedSomethingClass
        |}
      """.stripMargin
    checkTextHasNoErrors(text)
  }

  def testSCL13138() = {
    val text =
      """
        |trait A[T] {
        |  type T
        |  def f(x : T) : Unit
        |}
      """.stripMargin
    checkTextHasNoErrors(text)
  }
}
