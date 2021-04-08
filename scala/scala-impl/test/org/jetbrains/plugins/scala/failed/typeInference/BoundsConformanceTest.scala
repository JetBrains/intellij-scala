package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

/**
  * Created by Anton Yalyshev on 14/07/16.
  */

class BoundsConformanceTest extends ScalaLightCodeInsightFixtureTestAdapter {

  override protected def shouldPass: Boolean = false

  def testSCL10692(): Unit = {
    checkTextHasNoErrors(
      """
        |trait A {
        |  type B[+T]
        |  type C[+T] <: B[T]
        |  def c: C[Int]
        |}
        |
        |object Q {
        |  val a: A = ???
        |  val b: a.B[Int] = a.c
        |}
      """.stripMargin
    )
  }

  def testSCL12287(): Unit = {
    checkTextHasNoErrors(
      """
        |  trait IdOf[+T]
        |
        |  class RichAnyRef[T](val x: T) extends AnyVal {
        |    def getId: IdOf[T] = ???
        |  }
        |  implicit def toRichAnyRef(x: AnyRef): RichAnyRef[x.type] = new RichAnyRef[x.type](x)
        |  val y: IdOf[String] = "".getId
      """.stripMargin
    )
  }

  def testSCL13020(): Unit = {
    checkTextHasNoErrors(
      """
        |def f[T <: AnyRef](x: T): { def ==(y: Any) : Boolean } = x
      """.stripMargin
    )
  }
}
