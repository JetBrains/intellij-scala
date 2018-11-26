package org.jetbrains.plugins.scala.lang.typeInference
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

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
}