package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

class OverloadedResolutionWithImplicitParamsTest extends ScalaLightCodeInsightFixtureTestAdapter {
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
}
