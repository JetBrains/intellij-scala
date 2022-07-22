package org.jetbrains.plugins.scala
package lang.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
class ParameterBoundedImplicitConversionTest extends ScalaLightCodeInsightFixtureTestAdapter {
  def testSCL13089(): Unit = {
    val text =
      """
        |import scala.language.implicitConversions
        |import scala.language.higherKinds
        |trait Foo[T]
        |trait FooBuild[M[_], Face] {
        |  implicit def fromNum[T <: Face, Out <: T](value : T) : M[Out] = ???
        |}
        |implicit object Foo extends FooBuild[Foo, scala.Int]
        |def tryme[T](t : Foo[T]) = ???
        |tryme(40)
      """.stripMargin
    checkTextHasNoErrors(text)
  }

  def testSCL17364(): Unit = checkTextHasNoErrors(
    """
      |implicit class Test[T](name: Int) {
      |  def withValue(value: T): Test[T] = this
      |}
      |
      |val test: Test[Boolean] = 123.withValue(true)
      |""".stripMargin
  )

  def testSCL17368(): Unit = checkTextHasNoErrors(
    """
      |trait TestClassA
      |trait TestClassB
      |object TestClassA extends TestUtil[TestClassA]
      |
      |trait TestUtil[I <: TestClassA] {
      |  implicit def convert(classA: TestClassA)(implicit smt: I): TestClassB = null
      |}
      |implicit val instA: TestClassA = null
      |val instB: TestClassB = instA
      |""".stripMargin
  )
}