package org.jetbrains.plugins.scala
package lang.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
class OverloadedResolutionWithImplicitParamsTest extends ScalaLightCodeInsightFixtureTestCase {
  override def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_12

  def testSCL17518(): Unit = checkTextHasNoErrors(
    """
      |class ErasureTest[A](data: A) {
      |  def method[V](func1: A => Option[Long]): Long = func1(data).get
      |  def method[V](func2: A => Long)(implicit dummy: DummyImplicit): Long = func2(data)
      |}
      |case class Dummy(
      |    val1: Option[Long],
      |    val2: Long
      |)
      |object TypeErasureTest extends App {
      |  val data = Dummy(Some(1L), 2L)
      |  val erasure = new ErasureTest(data)
      |  val one = erasure.method(_.val1)
      |  val two = erasure.method(_.val2)
      |}""".stripMargin
  )

  def testSCL21273(): Unit = checkTextHasNoErrors(
    """
      |import scala.language.implicitConversions
      |
      |object Main {
      |
      |  trait AF[+R]
      |  trait F[+R]
      |
      |  implicit def fFromAF[R](f: AF[R]): F[R] = ???
      |  def future[T](x: =>T): AF[T] = ???
      |
      |  implicit class FDoneOps(f: AF[Double]) {
      |
      |    def whenDone[X](t: => F[X]): AF[X] = ???
      |    def whenDone(t: => Double)(implicit dummyHack: DummyImplicit): AF[Double] = ???
      |  }
      |
      |  def main(args: Array[String]): Unit = {
      |    future(0.0).whenDone(future(0.0))
      |  }
      |}""".stripMargin
  )
}
