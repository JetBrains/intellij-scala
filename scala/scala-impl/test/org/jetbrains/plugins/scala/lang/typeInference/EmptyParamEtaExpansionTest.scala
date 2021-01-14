package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.LatestScalaVersions.{Scala_2_12, Scala_2_13}
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

class EmptyParamEtaExpansionTest extends ScalaLightCodeInsightFixtureTestAdapter {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == Scala_2_13

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

  def testSCL18525(): Unit = checkTextHasNoErrors(
    """
      |class Bug01 {
      |  def get(): Int = 101
      |}
      |
      |object Bug01 {
      |
      |  def invoke(f: () => Int): Int = f()
      |
      |  def main(args: Array[String]): Unit = {
      |    println(
      |      invoke(
      |        new Bug01().get
      |      )
      |    )
      |  }
      |}
      |""".stripMargin
  )

  def testSCL18589(): Unit = checkTextHasNoErrors(
    """
      |def helloWorld(): Unit = println(s"  function helloWorld called")
      |def callFunc(func: () => Unit) = func()
      |callFunc(helloWorld)
      |""".stripMargin
  )
}

class EmpptyParamEtaExpansion_2_12 extends ScalaLightCodeInsightFixtureTestAdapter {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == Scala_2_12

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
