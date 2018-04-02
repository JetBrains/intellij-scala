package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.junit.experimental.categories.Category

/**
  * @author anton.yalyshev
  * @since 14.04.16.
  */
@Category(Array(classOf[PerfCycleTests]))
class ApplyConfomanceTest extends ScalaLightCodeInsightFixtureTestAdapter {

  override protected def shouldPass: Boolean = false

  def testSCL12708(): Unit = {
    checkTextHasNoErrors(
      s"""
         |trait T
         |case object V extends T
         |
         |case class Clz(exprs: T*)
         |
         |def create[P](args: Seq[T], creator: (T*) => Clz) = {
         |  creator(args :_*)
         |}
         |
         |create[Clz](Seq(V,  V, V), Clz.apply)
      """.stripMargin)
  }

  def testSCL11912(): Unit = {
    checkTextHasNoErrors(
      s"""
         |object test {
         |  final case class Kleisli[F[_], A, B](run: A => F[B])
         |  val f = Kleisli { (x: Int) => Some(x + 1) }
         |}
      """.stripMargin)
  }

  def testSCL13046(): Unit = {
    checkTextHasNoErrors(
      s"""
         |trait Test {
         |  type Repr
         |}
         |
         |object Test {
         |  def getRepr(t: Test): t.Repr = ???
         |
         |  def whatever(t: Test): Unit = {
         |    val value: t.Repr = getRepr(t)
         |  }
         |}
      """.stripMargin)
  }
}
