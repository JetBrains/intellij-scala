package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.LatestScalaVersions.{Scala_2_12, Scala_2_13}
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

class EmptyParamEtaExpansion extends ScalaLightCodeInsightFixtureTestAdapter {
  override implicit def version: ScalaVersion = Scala_2_13

  def testSCL18172(): Unit = checkTextHasNoErrors(
    """
      |import java.util.concurrent.{ExecutorService, Future}
      |type Par[A] = ExecutorService => Future[A]
      |def join[A](a: Par[Par[A]]): Par[A] = es => {
      |  val value: Par[A] = a(es).get
      |  ???
      |}
      |""".stripMargin
  )
}

class EmpptyParamEtaExpansion_2_12 extends ScalaLightCodeInsightFixtureTestAdapter {
  override implicit def version: ScalaVersion = Scala_2_12

  def testSCL18172(): Unit = checkHasErrorAroundCaret(
    s"""
      |import java.util.concurrent.{ExecutorService, Future}
      |type Par[A] = ExecutorService => Future[A]
      |def join[A](a: Par[Par[A]]): Par[A] = es => {
      |  val value: Par[A] = ${CARET}a(es).get
      |  ???
      |}""".stripMargin
  )
}
